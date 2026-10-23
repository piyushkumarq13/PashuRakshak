import { randomUUID } from 'node:crypto';
import { db } from '../../../db/client.js';
import { appendAuditLog } from './auditLog.js';
import { sendPushToUid } from '../../../core/services/push.js';

/**
 * Cluster detection for symptom reports.
 *
 * Plain JS: bounding-box pre-filter in SQL (±0.05°), then Haversine refinement
 * in-process. A cluster is 3+ reports (including the triggering one) created in
 * the last 7 days within 5 km of each other.
 */

export const WINDOW_MS = 7 * 24 * 60 * 60 * 1000; // last 7 days
export const BBOX_DEG = 0.05; // ±0.05 degrees pre-filter
export const RADIUS_KM = 5;
export const MIN_REPORTS = 3;

const toRad = (deg) => (deg * Math.PI) / 180;

/** Great-circle distance in kilometers (Earth mean radius 6371 km). */
export function haversineKm(lat1, lon1, lat2, lon2) {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(a));
}

function round(value, digits = 3) {
  return Number(value.toFixed(digits));
}

/**
 * Detects a cluster around a newly saved report.
 *
 * On success:
 *  - inserts a pashu_alerts row (recipient_role='vet') describing the cluster
 *  - flips every involved report still in 'reported' to 'vet_assigned'
 *  - pushes to every device registered with role='vet' for [appId] (best-effort)
 *
 * Returns { clustered, count, reportIds, alertId? } — never throws on its own
 * (callers in request handlers should still wrap in try/catch).
 */
export async function detectClusterForReport(report, appId = null) {
  const lat = Number(report.latitude);
  const lon = Number(report.longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return { clustered: false, count: 0, reportIds: [] };
  }

  const cutoff = Date.now() - WINDOW_MS;

  // Bounding-box pre-filter — cheap SQL scan, then exact distance in JS.
  const candidates = await db.execute({
    sql: `SELECT id, latitude, longitude FROM pashu_symptom_reports
      WHERE created_at >= ?
        AND id != ?
        AND latitude BETWEEN ? AND ?
        AND longitude BETWEEN ? AND ?`,
    args: [
      cutoff,
      report.id,
      lat - BBOX_DEG,
      lat + BBOX_DEG,
      lon - BBOX_DEG,
      lon + BBOX_DEG,
    ],
  });

  const nearby = candidates.rows.filter((row) =>
    haversineKm(lat, lon, Number(row.latitude), Number(row.longitude)) <= RADIUS_KM,
  );

  const involvedIds = [report.id, ...nearby.map((row) => row.id)];
  if (involvedIds.length < MIN_REPORTS) {
    return { clustered: false, count: involvedIds.length, reportIds: involvedIds };
  }

  // Deterministic message (rounded coords) so retried POSTs hit the dedupe below.
  const message =
    `Disease cluster detected: ${involvedIds.length} reports within ${RADIUS_KM} km ` +
    `of (${round(lat)}, ${round(lon)}) in the last 7 days. Field check recommended.`;

  const existing = await db.execute({
    sql: `SELECT id FROM pashu_alerts
      WHERE recipient_role = 'vet' AND message = ? AND created_at >= ?`,
    args: [message, cutoff],
  });

  let alertId = existing.rows[0]?.id ?? null;
  const alertCreated = !alertId;
  if (alertCreated) {
    alertId = randomUUID();
    await db.execute({
      sql: `INSERT INTO pashu_alerts (id, recipient_role, recipient_id, message, read, created_at)
        VALUES (?, 'vet', 'all-vets', ?, 0, ?)`,
      args: [alertId, message, Date.now()],
    });
    // Best-effort push to registered vets for this app — never fails detection.
    void notifyVets(appId, message).catch((error) => {
      console.error('[clusterDetection] vet notification failed:', error?.message ?? error);
    });
  }

  const placeholders = involvedIds.map(() => '?').join(', ');
  await db.execute({
    sql: `UPDATE pashu_symptom_reports SET status = 'vet_assigned'
      WHERE status = 'reported' AND id IN (${placeholders})`,
    args: involvedIds,
  });

  return {
    clustered: true,
    count: involvedIds.length,
    reportIds: involvedIds,
    alertId,
    message,
    alertCreated,
  };
}

/**
 * Categorize a report's risk and optionally auto-assign a vet by village.
 *
 * - ai_risk_category: 'high' if riskScore >= 60 OR flagged as part of a cluster;
 *   'mid' if riskScore >= 30; else 'low'.
 * - If report.village is present: query pashu_vet_profiles for a vet whose
 *   service_areas JSON text contains report.village. If found:
 *   UPDATE assigned_vet_id and create a pashu_alerts row targeted at
 *   that vet's user id.
 * - If no village or no matching vet: leave assigned_vet_id null,
 *   log a warning, do not throw.
 */
export async function categorizeAndAssign(report, appId) {
  const riskScore = Number(report.riskScore) || 0;
  const isCluster = !!report.clustered;
  const village = report.village ?? null;

  let category;
  if (riskScore >= 60 || isCluster) {
    category = 'high';
  } else if (riskScore >= 30) {
    category = 'mid';
  } else {
    category = 'low';
  }

  try {
    await db.execute({
      sql: 'UPDATE pashu_symptom_reports SET ai_risk_category = ? WHERE id = ?',
      args: [category, report.id],
    });

    // Re-pushes (for example after a photo upload) must not assign the same vet
    // again or create a duplicate notification.
    const current = await db.execute({
      sql: 'SELECT assigned_vet_id FROM pashu_symptom_reports WHERE id = ?',
      args: [report.id],
    });
    const existingVetId = current.rows[0]?.assigned_vet_id;
    if (existingVetId && existingVetId !== 'null' && existingVetId !== 'undefined') return;

    if (!village) {
      console.warn(`[categorizeAndAssign] No village for report ${report.id}; assigned_vet_id left null`);
      return;
    }

    const vetResult = await db.execute({
      sql: `SELECT u.id FROM users u
        JOIN pashu_vet_profiles v ON u.id = v.user_id
        LEFT JOIN vet_applications a ON a.user_id = u.id
        WHERE u.role = 'vet'
          AND (a.status = 'approved' OR a.id IS NULL)
          AND v.service_areas LIKE ?
        LIMIT 1`,
      args: [`%${village}%`],
    });

    if (vetResult.rows.length === 0) {
      console.warn(`[categorizeAndAssign] No vet found for village ${village} on report ${report.id}`);
      return;
    }

    const vetUserId = vetResult.rows[0].id;
    const now = Date.now();

    await db.execute({
      sql: 'UPDATE pashu_symptom_reports SET assigned_vet_id = ? WHERE id = ?',
      args: [vetUserId, report.id],
    });

    await appendAuditLog(db, {
      actor: 'system',
      action: 'vet_assigned',
      entityType: 'report',
      entityId: report.id,
      payload: { vetUserId, village, category },
    });

    const alertId = randomUUID();
    const message = `AI risk ${category} — report ${report.id} in ${village} assigned to vet`;
    await db.execute({
      sql: `INSERT INTO pashu_alerts (id, recipient_role, recipient_id, message, read, created_at)
        VALUES (?, 'vet', ?, ?, 0, ?)`,
      args: [alertId, vetUserId, message, now],
    });
  } catch (error) {
    console.error(`[categorizeAndAssign] failed for report ${report.id}:`, error);
  }
}

/**
 * Pushes the cluster message to every device_tokens row with role='vet' for this app.
 * Best-effort and parallel: one slow FCM call must not hold up report ingestion.
 */
async function notifyVets(appId, message) {
  if (!appId) return;

  try {
    const vets = await db.execute({
      sql: `SELECT DISTINCT uid FROM device_tokens
        WHERE app_id = ? AND role = 'vet' AND fcm_token IS NOT NULL AND fcm_token != ''`,
      args: [appId],
    });

    await Promise.allSettled(
      vets.rows.map(async (row) => {
        try {
          await sendPushToUid(row.uid, 'PashuRakshak cluster alert', message);
        } catch (error) {
          console.error('[clusterDetection] vet push failed for uid', row.uid, error?.message ?? error);
        }
      }),
    );
  } catch (error) {
    console.error('[clusterDetection] vet lookup failed:', error?.message ?? error);
  }
}

const CLUSTER_GRID_DEG = 0.1;

function clusterGridKey(latitude, longitude) {
  return `${Math.floor(latitude / CLUSTER_GRID_DEG)}:${Math.floor(longitude / CLUSTER_GRID_DEG)}`;
}

/**
 * Groups reports into connected components (single-linkage, 5 km).
 *
 * The old implementation compared every report against every group member
 * repeatedly, which blocks the event loop for seconds as report volume grows.
 * A spatial grid limits distance checks to nearby cells while producing the
 * exact same connected components.
 */
function groupConnectedReports(reports) {
  const parent = new Int32Array(reports.length);
  for (let i = 0; i < parent.length; i += 1) parent[i] = i;

  const find = (start) => {
    let root = start;
    while (parent[root] !== root) root = parent[root];
    let current = start;
    while (parent[current] !== current) {
      const next = parent[current];
      parent[current] = root;
      current = next;
    }
    return root;
  };

  const union = (left, right) => {
    const a = find(left);
    const b = find(right);
    if (a !== b) parent[b] = a;
  };

  const cells = new Map();
  for (let i = 0; i < reports.length; i += 1) {
    const latitude = Number(reports[i].latitude);
    const longitude = Number(reports[i].longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue;
    const key = clusterGridKey(latitude, longitude);
    const bucket = cells.get(key);
    if (bucket) bucket.push(i);
    else cells.set(key, [i]);
  }

  for (const [key, indexes] of cells) {
    const [cellX, cellY] = key.split(':').map(Number);
    const nearby = [];
    for (let dx = -2; dx <= 2; dx += 1) {
      for (let dy = -2; dy <= 2; dy += 1) {
        const bucket = cells.get(`${cellX + dx}:${cellY + dy}`);
        if (!bucket) continue;
        for (const index of bucket) nearby.push(index);
      }
    }

    for (const left of indexes) {
      const a = reports[left];
      const latA = Number(a.latitude);
      const lonA = Number(a.longitude);
      for (const right of nearby) {
        if (right <= left) continue;
        const b = reports[right];
        if (haversineKm(latA, lonA, Number(b.latitude), Number(b.longitude)) <= RADIUS_KM) {
          union(left, right);
        }
      }
    }
  }

  const groups = new Map();
  for (let i = 0; i < reports.length; i += 1) {
    const root = find(i);
    const group = groups.get(root);
    if (group) group.push(reports[i]);
    else groups.set(root, [reports[i]]);
  }
  return [...groups.values()];
}

/**
 * Currently active clusters: greedy single-linkage grouping over non-resolved
 * reports from the last 7 days, using the same 5 km radius / 3-report minimum.
 */
export async function getActiveClusters() {
  const cutoff = Date.now() - WINDOW_MS;

  const result = await db.execute({
    sql: `SELECT id, latitude, longitude, status, created_at, farmer_id, animal_id
      FROM pashu_symptom_reports
      WHERE created_at >= ? AND status != 'resolved'
      ORDER BY created_at ASC, id ASC`,
    args: [cutoff],
  });

  const groups = groupConnectedReports(result.rows);
  const clusters = [];

  for (const group of groups) {
    if (group.length < MIN_REPORTS) continue;

    let centerLat = 0;
    let centerLng = 0;
    let firstReportAt = Infinity;
    let lastReportAt = -Infinity;
    for (const report of group) {
      centerLat += Number(report.latitude);
      centerLng += Number(report.longitude);
      const createdAt = Number(report.created_at);
      if (createdAt < firstReportAt) firstReportAt = createdAt;
      if (createdAt > lastReportAt) lastReportAt = createdAt;
    }

    clusters.push({
      reportCount: group.length,
      reportIds: group.map((report) => report.id),
      centerLat: round(centerLat / group.length, 5),
      centerLng: round(centerLng / group.length, 5),
      radiusKm: RADIUS_KM,
      windowDays: 7,
      firstReportAt,
      lastReportAt,
      statuses: [...new Set(group.map((report) => report.status))],
    });
  }

  return clusters;
}

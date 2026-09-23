/**
 * Postal pincode lookup (api.postalpincode.in) shared by the public pincode
 * route and district resolution for vet applications.
 */

export async function fetchPincodeOffices(code) {
  const response = await fetch(`https://api.postalpincode.in/pincode/${code}`);
  const data = await response.json();

  if (!Array.isArray(data) || data.length === 0) return null;

  const firstRecord = data[0];
  if (firstRecord.Status === 'Error' || !Array.isArray(firstRecord.PostOffice) || firstRecord.PostOffice.length === 0) {
    return null;
  }

  const offices = firstRecord.PostOffice.map((office) => ({
    name: office.Name,
    branchType: office.BranchType,
    deliveryStatus: office.DeliveryStatus,
    district: office.District,
    state: office.State,
    areaName: office.AreaName,
    pincode: office.Pincode,
  }));

  return { pincode: code, offices };
}

/**
 * Resolves { district, state } for a 6-digit pincode from the postal API.
 * Returns null when the pincode cannot be resolved.
 */
export async function resolveDistrict(pincode) {
  try {
    const result = await fetchPincodeOffices(pincode);
    if (!result || result.offices.length === 0) return null;

    // Post offices for one pincode usually share a district; pick the most common.
    const counts = new Map();
    for (const office of result.offices) {
      const district = String(office.district ?? '').trim();
      const state = String(office.state ?? '').trim();
      if (!district) continue;
      const key = `${district}|${state}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    if (counts.size === 0) return null;

    const [best] = [...counts.entries()].sort((a, b) => b[1] - a[1]);
    const [district, state] = best[0].split('|');
    return { district, state: state || null };
  } catch (error) {
    console.error('[pincode] district resolution failed:', error?.message ?? error);
    return null;
  }
}

import { randomUUID } from 'node:crypto';
import { db } from '../../../db/client.js';
import Groq from 'groq-sdk';
import { env } from '../../../config/env.js';

const groq = new Groq({ apiKey: env.groqApiKey, timeout: 25_000, maxRetries: 0 });

const MODEL = 'openai/gpt-oss-120b';

// gpt-oss models spend output tokens on internal reasoning before the visible
// answer — a small max_tokens budget therefore truncates the reply mid-way.
// 16384 leaves ample room; 'low' reasoning effort keeps answers fast and puts
// the budget into the visible text instead of hidden chain-of-thought.
const MAX_COMPLETION_TOKENS = 16384;
const REASONING_EFFORT = 'low';

const SYSTEM_PROMPT = `You are a livestock health advisory assistant for a farming app called PashuRakshak.

Your role: give general preventive and first-aid guidance only.

Guidelines:
- Suggest isolation of the affected animal, ensuring hydration, limiting herd contact, and maintaining hygiene.
- NEVER prescribe specific medications, dosages, or treatment protocols.
- Always close by saying: "A vet will follow up shortly. This is not a diagnosis."
- Use simple language a farmer who may not be highly literate can understand.

Language: respond entirely in Hindi if the farmer's preferred language is 'hi', otherwise respond entirely in English.`;

function buildUserPrompt(report, riskCategory) {
  return `Here are the details of a reported case:

Species: ${report.species ?? 'unknown'}
Symptoms: ${report.symptoms ?? 'unknown'}
Risk category: ${riskCategory}
Village: ${report.village ?? 'not specified'}
Risk score: ${report.riskScore ?? 'not available'}
Risk breakdown: ${report.riskBreakdown ?? 'not available'}

Please provide general preventive and first-aid guidance based on the information above.`;
}

/**
 * Generate an AI advisory for a report.
 * Builds a prompt with species, symptoms, risk category.
 * Saves the response into pashu_ai_responses.
 * Returns the response text.
 */
export async function generateAdvisory(report, farmerPreferredLanguage) {
  const riskCategory = report.ai_risk_category ?? 'low';
  const language = farmerPreferredLanguage === 'hi' ? 'Hindi' : 'English';

  const userPrompt = buildUserPrompt(report, riskCategory);

  try {
    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages: [
        { role: 'system', content: `${SYSTEM_PROMPT}\n\nRespond in ${language}.` },
        { role: 'user', content: userPrompt },
      ],
      temperature: 0.7,
      max_tokens: MAX_COMPLETION_TOKENS,
      reasoning_effort: REASONING_EFFORT,
    });

    const responseText = completion.choices[0]?.message?.content ?? '';
    const now = Date.now();
    const id = randomUUID();

    await db.execute({
      sql: `INSERT INTO pashu_ai_responses (id, report_id, farmer_id, response_text, model, created_at)
        VALUES (?, ?, ?, ?, ?, ?)`,
      args: [id, report.id, report.farmerId, responseText, MODEL, now],
    });

    const convId = randomUUID();
    await db.execute({
      sql: `INSERT INTO pashu_ai_conversations (id, report_id, farmer_id, full_history, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)`,
      args: [convId, report.id, report.farmerId, JSON.stringify([{ role: 'assistant', content: responseText }]), now, now],
    });

    return responseText;
  } catch (error) {
    console.error('[aiAdvisory] generateAdvisory failed:', error?.message ?? error);
    return null;
  }
}

/**
 * Continue a conversation for a report.
 * Loads (or creates) the pashu_ai_conversations row for that report,
 * appends the user message to the stored full history, but sends ONLY
 * THE LAST 20 MESSAGES plus the system prompt to Groq as context
 * (to cap token usage/cost).
 * Appends the assistant reply to the FULL stored history (not just
 * the capped context), saves, returns the reply.
 */
const UNAVAILABLE_REPLY = 'AI assistance is unavailable right now. Please try again shortly.';

export async function generateStandaloneChatReply(history, farmerPreferredLanguage) {
  const language = farmerPreferredLanguage === 'hi' ? 'Hindi' : 'English';
  const cappedContext = (Array.isArray(history) ? history : []).slice(-20);

  try {
    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages: [
        { role: 'system', content: `${SYSTEM_PROMPT}\n\nRespond in ${language}.` },
        ...cappedContext,
      ],
      temperature: 0.7,
      max_tokens: MAX_COMPLETION_TOKENS,
      reasoning_effort: REASONING_EFFORT,
    });

    const reply = completion.choices[0]?.message?.content ?? '';
    return reply.trim() ? reply : UNAVAILABLE_REPLY;
  } catch (error) {
    console.error('[aiAdvisory] standalone Groq call failed:', error?.message ?? error);
    return UNAVAILABLE_REPLY;
  }
}

const VET_SYSTEM_PROMPT = `You are a veterinary clinical decision-support assistant inside PashuRakshak, speaking with a licensed veterinarian.

Guidelines:
- Use professional veterinary terminology and reason from the case details provided.
- You may discuss differential diagnoses, diagnostic tests, treatment options, drug classes, supportive care, isolation, and herd-level precautions.
- Clearly separate confirmed findings from possibilities. Never invent history, laboratory results, or examination findings that are not present.
- Ask for missing clinical information when it matters.
- When discussing medicines, include conservative guidance and explicitly require the vet to confirm the dose against a current formulary, the animal's weight, contraindications, and local withdrawal-period regulations.
- Flag zoonotic, notifiable, or emergency conditions and recommend containment or official reporting when relevant.
- Do not claim to replace clinical judgment. End every response with: "Clinical judgment and local regulations take precedence."`;

function displayValue(value, fallback = 'not recorded') {
  if (value === undefined || value === null) return fallback;
  const text = String(value).trim();
  return text === '' ? fallback : text;
}

function displayTimestamp(value) {
  const millis = Number(value);
  if (!Number.isFinite(millis) || millis <= 0) return 'not recorded';
  return new Date(millis).toISOString();
}

/** Formats the report + animal row as a compact, stable case briefing for the AI. */
export function formatVetCaseContext(report) {
  if (!report) return null;
  const animal = displayValue(report.animal_name, 'unnamed animal');
  const species = displayValue(report.animal_species, 'unknown species');
  const qrVerified = report.qr_verified === 1 || report.qr_verified === true;
  const lines = [
    `Case ID: ${displayValue(report.id)}`,
    `Animal: ${animal} (${species})`,
    `Village: ${displayValue(report.village, 'not recorded')}`,
    `Reported at: ${displayTimestamp(report.created_at)}`,
    `Current status: ${displayValue(report.status)}`,
    `Symptoms: ${displayValue(report.symptoms)}`,
    `Disease description: ${displayValue(report.disease_description)}`,
    `Risk score: ${displayValue(report.risk_score)}`,
    `Risk category: ${displayValue(report.ai_risk_category)}`,
    `Risk breakdown: ${displayValue(report.risk_breakdown)}`,
    `Reported location: ${displayValue(report.latitude)}, ${displayValue(report.longitude)}`,
    `QR verified: ${qrVerified ? 'yes' : 'no'}`,
    `Vet assessment: ${displayValue(report.vet_assessment)}`,
    `Resolution note: ${displayValue(report.resolution_note)}`,
    `Resolved at: ${displayTimestamp(report.resolved_at)}`,
  ];
  return lines.join('\n');
}

/**
 * Vet chat replies. A case-scoped conversation gets a fresh case briefing on
 * every turn, followed by the recent conversation context.
 */
export async function generateVetChatReply(
  history,
  { preferredLanguage = 'en', reportContext = null } = {},
) {
  const language = preferredLanguage === 'hi' ? 'Hindi' : 'English';
  const cappedContext = (Array.isArray(history) ? history : []).slice(-20);
  const systemContent = reportContext
    ? `${VET_SYSTEM_PROMPT}\n\nCASE CONTEXT\n${reportContext}\n\nRespond in ${language}.`
    : `${VET_SYSTEM_PROMPT}\n\nRespond in ${language}.`;

  try {
    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages: [
        { role: 'system', content: systemContent },
        ...cappedContext,
      ],
      temperature: 0.4,
      max_tokens: MAX_COMPLETION_TOKENS,
      reasoning_effort: REASONING_EFFORT,
    });

    const reply = completion.choices[0]?.message?.content ?? '';
    return reply.trim() ? reply : UNAVAILABLE_REPLY;
  } catch (error) {
    console.error('[aiAdvisory] vet chat Groq call failed:', error?.message ?? error);
    return UNAVAILABLE_REPLY;
  }
}

export async function continueConversation(reportId, farmerId, userMessage, farmerPreferredLanguage) {
  const language = farmerPreferredLanguage === 'hi' ? 'Hindi' : 'English';
  const now = Date.now();

  let conversation;
  try {
    conversation = await db.execute({
      sql: 'SELECT * FROM pashu_ai_conversations WHERE report_id = ? AND farmer_id = ?',
      args: [reportId, farmerId],
    });
  } catch (error) {
    console.error('[aiAdvisory] conversation lookup failed:', error?.message ?? error);
    return UNAVAILABLE_REPLY;
  }

  let fullHistory;
  if (conversation.rows.length === 0) {
    fullHistory = [];
  } else {
    try {
      fullHistory = JSON.parse(conversation.rows[0].full_history);
    } catch {
      fullHistory = [];
    }
  }

  // Append the new user message to the full history (always persisted below,
  // even if the Groq call fails, so the conversation is never lost).
  fullHistory.push({ role: 'user', content: userMessage });

  let assistantReply = '';
  try {
    // Cap context: only send the last 20 messages to Groq
    const cappedContext = fullHistory.slice(-20);
    const messages = [
      { role: 'system', content: `${SYSTEM_PROMPT}\n\nRespond in ${language}.` },
      ...cappedContext,
    ];

    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages,
      temperature: 0.7,
      max_tokens: MAX_COMPLETION_TOKENS,
      reasoning_effort: REASONING_EFFORT,
    });

    assistantReply = completion.choices[0]?.message?.content ?? '';
  } catch (error) {
    console.error('[aiAdvisory] Groq call failed:', error?.message ?? error);
  }

  if (!assistantReply.trim()) {
    assistantReply = UNAVAILABLE_REPLY;
  }

  // Always append + persist, so the conversation stays coherent and survives reloads.
  fullHistory.push({ role: 'assistant', content: assistantReply });

  try {
    const historyStr = JSON.stringify(fullHistory);

    if (conversation.rows.length === 0) {
      const convId = randomUUID();
      await db.execute({
        sql: `INSERT INTO pashu_ai_conversations (id, report_id, farmer_id, full_history, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)`,
        args: [convId, reportId, farmerId, historyStr, now, now],
      });
    } else {
      await db.execute({
        sql: `UPDATE pashu_ai_conversations SET full_history = ?, updated_at = ?
          WHERE id = ?`,
        args: [historyStr, now, conversation.rows[0].id],
      });
    }
  } catch (error) {
    console.error('[aiAdvisory] conversation save failed:', error?.message ?? error);
  }

  return assistantReply;
}

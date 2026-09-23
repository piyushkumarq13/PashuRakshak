import { randomUUID } from 'node:crypto';
import { db } from '../../../db/client.js';
import Groq from 'groq-sdk';
import { env } from '../../../config/env.js';

const groq = new Groq({ apiKey: env.groqApiKey });

const MODEL = 'llama-3.3-70b-versatile';

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
      max_tokens: 500,
    });

    const responseText = completion.choices[0]?.message?.content ?? '';
    const now = Date.now();
    const id = randomUUID();

    await db.execute({
      sql: `INSERT INTO pashu_ai_responses (id, report_id, farmer_id, response_text, model, created_at)
        VALUES (?, ?, ?, ?, ?, ?)`,
      args: [id, report.id, report.farmerId, responseText, MODEL, now],
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
 * THE LAST 10 MESSAGES plus the system prompt to Groq as context
 * (to cap token usage/cost).
 * Appends the assistant reply to the FULL stored history (not just
 * the capped context), saves, returns the reply.
 */
const UNAVAILABLE_REPLY = 'AI assistance is unavailable right now. Please try again shortly.';

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
    // Cap context: only send the last 10 messages to Groq
    const cappedContext = fullHistory.slice(-10);
    const messages = [
      { role: 'system', content: `${SYSTEM_PROMPT}\n\nRespond in ${language}.` },
      ...cappedContext,
    ];

    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages,
      temperature: 0.7,
      max_tokens: 500,
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

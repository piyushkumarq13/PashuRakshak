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
export async function continueConversation(reportId, farmerId, userMessage, farmerPreferredLanguage) {
  const language = farmerPreferredLanguage === 'hi' ? 'Hindi' : 'English';
  const now = Date.now();

  try {
    let conversation = await db.execute({
      sql: 'SELECT * FROM pashu_ai_conversations WHERE report_id = ? AND farmer_id = ?',
      args: [reportId, farmerId],
    });

    let fullHistory;
    if (conversation.rows.length === 0) {
      fullHistory = [];
    } else {
      fullHistory = JSON.parse(conversation.rows[0].full_history);
    }

    // Append the new user message to the full history
    fullHistory.push({ role: 'user', content: userMessage });

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

    const assistantReply = completion.choices[0]?.message?.content ?? '';

    // Append assistant reply to full history
    fullHistory.push({ role: 'assistant', content: assistantReply });

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

    return assistantReply;
  } catch (error) {
    console.error('[aiAdvisory] continueConversation failed:', error?.message ?? error);
    return null;
  }
}

import Groq from 'groq-sdk';
import { env } from '../../../config/env.js';

const groq = new Groq({ apiKey: env.groqApiKey });
const MODEL = 'openai/gpt-oss-120b';
const MAX_COMPLETION_TOKENS = 16384;
const REASONING_EFFORT = 'low';

const SYSTEM_PROMPT = `You are a livestock disease triage assistant for PashuRakshak, a veterinary reporting app.

Given the animal species, observed symptoms, and the farmer's free-text disease description, estimate how serious the case likely is.

You MUST respond with ONLY a compact JSON object (no markdown fences, no extra text):
{"score": <integer 0-100 where 100 is most severe>, "analysis": "<2-4 sentence plain-language analysis>", "likely_condition": "<short name or 'unknown'>", "severity": "low"|"medium"|"high"}`;

/**
 * Ask the model to score a case from its symptoms + disease description.
 * Returns { score, analysis, likelyCondition, severity } or null on failure.
 */
export async function analyzeRisk({ species, symptoms, diseaseDescription }) {
  const symptomText = Array.isArray(symptoms) ? symptoms.join(', ') : String(symptoms ?? '');
  const userPrompt = `Species: ${species ?? 'unknown'}
Symptoms: ${symptomText || 'none specified'}
Farmer description of the disease: ${diseaseDescription?.trim() || 'not provided'}

Analyze the case and return only the JSON object.`;

  try {
    const completion = await groq.chat.completions.create({
      model: MODEL,
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: userPrompt },
      ],
      temperature: 0.3,
      max_tokens: MAX_COMPLETION_TOKENS,
      reasoning_effort: REASONING_EFFORT,
    });

    const text = completion.choices[0]?.message?.content ?? '';
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return null;
    const parsed = JSON.parse(jsonMatch[0]);
    const score = Math.max(0, Math.min(100, Math.round(Number(parsed.score) || 0)));
    return {
      score,
      analysis: String(parsed.analysis ?? '').trim(),
      likelyCondition: String(parsed.likely_condition ?? '').trim(),
      severity: String(parsed.severity ?? '').trim() || (score >= 60 ? 'high' : score >= 30 ? 'medium' : 'low'),
    };
  } catch (error) {
    console.error('[aiRiskScore] analyzeRisk failed:', error?.message ?? error);
    return null;
  }
}

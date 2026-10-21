import app from '../src/app.js';

// Vercel serverless entry point. `src/app.js` exports the Express app without
// calling app.listen(), so the same code powers Render (long-running) and
// Vercel (serverless) with no behavioural differences.
export default app;

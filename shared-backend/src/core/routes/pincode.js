import { Router } from 'express';
import { fetchPincodeOffices } from '../services/pincode.js';

const router = Router();

/**
 * GET /api/v1/core/pincode/:code
 * Public endpoint — calls the PostalPincode API server-side and returns
 * a simplified list of area / post-office names for the given pincode.
 */
router.get('/pincode/:code', async (req, res) => {
  const code = req.params.code;

  if (!code || !/^\d{6}$/.test(code)) {
    return res.status(400).json({
      error: 'invalid_pincode',
      message: 'A valid 6-digit pincode is required.',
    });
  }

  try {
    const result = await fetchPincodeOffices(code);

    if (!result) {
      return res.status(404).json({
        error: 'pincode_not_found',
        message: `No post offices found for pincode ${code}.`,
      });
    }

    return res.status(200).json({ pincode: result.pincode, offices: result.offices });
  } catch (error) {
    console.error('[core/pincode] fetch failed:', error);
    return res.status(500).json({
      error: 'pincode_fetch_failed',
      message: 'Could not fetch pincode data from the postal service.',
    });
  }
});

export default router;

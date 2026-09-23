import { Router } from 'express';

const router = Router();

/**
 * GET /api/v1/core/pincode/:code
 * Public endpoint — calls the PostalPincode API server-side and returns
 * a simplified list of area / post-office names for the given pincode.
 */
router.get('/:code', async (req, res) => {
  const code = req.params.code;

  if (!code || !/^\d{6}$/.test(code)) {
    return res.status(400).json({
      error: 'invalid_pincode',
      message: 'A valid 6-digit pincode is required.',
    });
  }

  try {
    const url = `https://api.postalpincode.in/pincode/${code}`;
    const response = await fetch(url);
    const data = await response.json();

    if (!Array.isArray(data) || data.length === 0) {
      return res.status(404).json({
        error: 'pincode_not_found',
        message: `No post offices found for pincode ${code}.`,
      });
    }

    const firstRecord = data[0];
    if (firstRecord.Status === 'Error' || !firstRecord.PostOffice || firstRecord.PostOffice.length === 0) {
      return res.status(404).json({
        error: 'pincode_not_found',
        message: `No post offices found for pincode ${code}.`,
      });
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

    return res.status(200).json({ pincode: code, offices });
  } catch (error) {
    console.error('[core/pincode] fetch failed:', error);
    return res.status(500).json({
      error: 'pincode_fetch_failed',
      message: 'Could not fetch pincode data from the postal service.',
    });
  }
});

export default router;

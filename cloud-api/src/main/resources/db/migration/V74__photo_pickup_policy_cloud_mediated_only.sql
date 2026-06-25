update photos
set original_pickup_status = 'NOT_REQUIRED',
    updated_at = now()
where original_pickup_status = 'PENDING'
  and upload_target <> 'CLOUD_MEDIATED';

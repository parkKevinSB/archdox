update photos
set original_pickup_status = 'NOT_REQUIRED',
    pickup_error_message = null,
    updated_at = now()
where status = 'DELETED'
  and original_pickup_status in ('PENDING', 'FAILED');

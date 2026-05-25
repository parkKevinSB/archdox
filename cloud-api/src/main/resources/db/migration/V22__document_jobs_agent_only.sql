update document_jobs
   set worker_type = 'ARCHDOX_AGENT'
 where worker_type = 'CLOUD';

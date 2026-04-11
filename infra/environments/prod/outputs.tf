output "nacional_queue_url" {
  value = aws_sqs_queue.nacional.url
}

output "hospitais_api_urls" {
  description = "URLs das APIs de cada hospital"
  value = {
    einstein_sp = module.hosp_einstein_sp.api_url
    hc_sp       = module.hosp_hc_sp.api_url
  }
}

output "hospitais_ws_urls" {
  description = "WebSocket URLs de cada hospital"
  value = {
    einstein_sp = module.hosp_einstein_sp.ws_url
    hc_sp       = module.hosp_hc_sp.ws_url
  }
}

output "hospitais_frontend_urls" {
  description = "URLs do painel clínico de cada hospital"
  value = {
    einstein_sp = module.hosp_einstein_sp.frontend_url
    hc_sp       = module.hosp_hc_sp.frontend_url
  }
}

output "hospitais_frontend_buckets" {
  value = {
    einstein_sp = module.hosp_einstein_sp.frontend_bucket_name
    hc_sp       = module.hosp_hc_sp.frontend_bucket_name
  }
}

output "hospitais_cloudfront_ids" {
  value = {
    einstein_sp = module.hosp_einstein_sp.cloudfront_distribution_id
    hc_sp       = module.hosp_hc_sp.cloudfront_distribution_id
  }
}

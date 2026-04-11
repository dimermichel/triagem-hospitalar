# ── S3 + CloudFront para o painel-clinico ─────────────────────────────────────

resource "aws_s3_bucket" "frontend" {
  count         = var.enable_frontend ? 1 : 0
  bucket        = "sus-triagem-${local.prefix}-frontend"
  force_destroy = var.env != "prod"
  tags          = local.tags
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  count                   = var.enable_frontend ? 1 : 0
  bucket                  = aws_s3_bucket.frontend[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_website_configuration" "frontend" {
  count  = var.enable_frontend ? 1 : 0
  bucket = aws_s3_bucket.frontend[0].id
  index_document { suffix = "index.html" }
  error_document { key = "index.html" }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  count                             = var.enable_frontend ? 1 : 0
  name                              = "sus-triagem-${local.prefix}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "frontend" {
  count               = var.enable_frontend ? 1 : 0
  enabled             = true
  default_root_object = "index.html"
  price_class         = "PriceClass_200"
  comment             = "Painel Triagem — ${local.prefix}"

  origin {
    domain_name              = aws_s3_bucket.frontend[0].bucket_regional_domain_name
    origin_id                = "S3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend[0].id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = local.tags
}

resource "aws_s3_bucket_policy" "frontend" {
  count  = var.enable_frontend ? 1 : 0
  bucket = aws_s3_bucket.frontend[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend[0].arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.frontend[0].arn
        }
      }
    }]
  })
}

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

# ── Build e deploy do painel-clinico por instância de hospital ─────────────────

locals {
  painel_dir = "${path.module}/../../../painel-clinico"

  # invoke_url do $default stage termina com "/" — removemos para evitar dupla barra
  api_url_clean = trimsuffix(aws_apigatewayv2_stage.triagem_stage.invoke_url, "/")
  ws_url_clean  = trimsuffix(aws_apigatewayv2_stage.ws_stage.invoke_url, "/")
}

resource "null_resource" "frontend_build_deploy" {
  count = var.enable_frontend ? 1 : 0

  triggers = {
    api_url       = local.api_url_clean
    ws_url        = local.ws_url_clean
    hospital_nome = var.hospital_nome
    bucket        = aws_s3_bucket.frontend[0].id
    dist_id       = aws_cloudfront_distribution.frontend[0].id
  }

  # Aguarda bucket e distribuição estarem prontos
  depends_on = [
    aws_s3_bucket_policy.frontend,
    aws_cloudfront_distribution.frontend,
  ]

  provisioner "local-exec" {
    command = <<-EOT
      set -e

      # Diretório temporário isolado por hospital — evita conflito quando
      # múltiplos módulos executam npm ci em paralelo no mesmo fonte.
      WORK_DIR=$(mktemp -d)
      trap 'rm -rf "$WORK_DIR"' EXIT

      echo "==> [${var.hospital_id}] Copiando fontes para $WORK_DIR..."
      cp -r "${local.painel_dir}/." "$WORK_DIR/"

      echo "==> [${var.hospital_id}] Instalando dependências..."
      cd "$WORK_DIR"
      npm ci --prefer-offline

      echo "==> [${var.hospital_id}] Gerando build com as variáveis do hospital..."
      VITE_API_URL="${local.api_url_clean}" \
      VITE_WS_URL="${local.ws_url_clean}" \
      VITE_HOSPITAL_NOME="${var.hospital_nome}" \
      npm run build

      echo "==> [${var.hospital_id}] Sincronizando com S3..."
      aws s3 sync "$WORK_DIR/dist/" s3://${aws_s3_bucket.frontend[0].id}/ \
        --delete \
        --cache-control "public,max-age=31536000,immutable" \
        --exclude "index.html"

      aws s3 cp "$WORK_DIR/dist/index.html" s3://${aws_s3_bucket.frontend[0].id}/index.html \
        --cache-control "no-cache,no-store,must-revalidate"

      echo "==> [${var.hospital_id}] Invalidando CloudFront ${aws_cloudfront_distribution.frontend[0].id}..."
      aws cloudfront create-invalidation \
        --distribution-id ${aws_cloudfront_distribution.frontend[0].id} \
        --paths "/*"

      echo "==> [${var.hospital_id}] Deploy concluído."
    EOT
  }
}

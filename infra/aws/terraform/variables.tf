variable "aws_region" {
  description = "AWS region used by the reference infrastructure."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used to prefix resources."
  type        = string
  default     = "highpay"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

output "eks_cluster_name" {
  value = aws_eks_cluster.main.name
}

output "ecr_repositories" {
  value = {
    for name, repo in aws_ecr_repository.services : name => repo.repository_url
  }
}

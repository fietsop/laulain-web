// ============================================================
//  Laulain Luxe Rentals — Jenkins CI/CD Pipeline
//
//  Triggers on every push to: main, develop, release/*
//
//  Pipeline flow:
//    1. Checkout     — Clone from GitHub
//    2. Test         — Run Maven unit tests
//    3. Build JAR    — Maven package
//    4. Docker Build — Build container image
//    5. Push ECR     — Push to Amazon ECR
//    6. Deploy EKS   — kubectl rolling update (main branch only)
//    7. Verify       — Health check
//    8. Notify       — Console summary
//
//  Jenkins Credentials required (Manage Jenkins → Credentials → Global):
//    github-credentials   Username/Password — GitHub username + Personal Access Token
//    aws-account-id       Secret text       — Your 12-digit AWS account number
//    aws-credentials      AWS Credentials   — IAM access key + secret
//    kubeconfig-laulain   Secret file       — kubeconfig for EKS cluster
// ============================================================

pipeline {

    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        // App
        APP_DIR        = 'app'

        // AWS / ECR
        AWS_REGION     = 'us-east-1'
        ECR_REPO_NAME  = 'laulain-luxe-rentals'

        // EKS
        EKS_CLUSTER    = 'laulain-eks-cluster'
        K8S_NAMESPACE  = 'laulain'
        K8S_DEPLOYMENT = 'laulain-app'
        K8S_CONTAINER  = 'laulain-app'
    }

    parameters {
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip unit tests (emergency deploys only)'
        )
        booleanParam(
            name: 'SKIP_DEPLOY',
            defaultValue: false,
            description: 'Build and push image but do not deploy to EKS'
        )
    }

    stages {

        // ----------------------------------------------------------
        // STAGE 1 — CHECKOUT
        // ----------------------------------------------------------
        stage('Checkout') {
            steps {
                // Checkout is handled automatically by Jenkins Pipeline SCM.
                // This stage just captures the git commit info for tagging.
                script {
                    env.GIT_SHA      = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_BRANCH_NAME = sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG    = "${env.GIT_SHA}-${BUILD_NUMBER}"

                    echo "Branch:    ${env.GIT_BRANCH_NAME}"
                    echo "Commit:    ${env.GIT_SHA}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        // ----------------------------------------------------------
        // STAGE 2 — UNIT TESTS
        // ----------------------------------------------------------
        stage('Unit Tests') {
            when {
                not { expression { params.SKIP_TESTS } }
            }
            steps {
                dir("${APP_DIR}") {
                    sh '''
                        mvn test \
                            -Dspring.profiles.active=test \
                            -B \
                            --no-transfer-progress
                    '''
                }
            }
            post {
                always {
                    junit(
                        testResults: "${APP_DIR}/target/surefire-reports/**/*.xml",
                        allowEmptyResults: true
                    )
                }
            }
        }

        // ----------------------------------------------------------
        // STAGE 3 — BUILD JAR
        // ----------------------------------------------------------
        stage('Build JAR') {
            steps {
                dir("${APP_DIR}") {
                    sh '''
                        mvn clean package \
                            -DskipTests \
                            -B \
                            --no-transfer-progress
                    '''
                }
                archiveArtifacts(
                    artifacts: "${APP_DIR}/target/*.jar",
                    fingerprint: true,
                    onlyIfSuccessful: true
                )
            }
        }

        // ----------------------------------------------------------
        // STAGE 4 — DOCKER BUILD
        // ----------------------------------------------------------
        stage('Docker Build') {
            steps {
                script {
                    // Resolve ECR registry URL using the AWS account ID credential
                    withCredentials([
                        string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')
                    ]) {
                        env.ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        env.IMAGE_FULL   = "${env.ECR_REGISTRY}/${ECR_REPO_NAME}:${env.IMAGE_TAG}"
                        env.IMAGE_LATEST = "${env.ECR_REGISTRY}/${ECR_REPO_NAME}:latest"
                    }

                    dir("${APP_DIR}") {
                        sh """
                            docker build \\
                                --build-arg GIT_COMMIT=${env.GIT_SHA} \\
                                --build-arg BUILD_NUMBER=${BUILD_NUMBER} \\
                                --tag ${env.IMAGE_FULL} \\
                                --tag ${env.IMAGE_LATEST} \\
                                .
                        """
                    }
                    echo "Image built: ${env.IMAGE_FULL}"
                }
            }
        }

        // ----------------------------------------------------------
        // STAGE 5 — PUSH TO ECR
        // ----------------------------------------------------------
        stage('Push to ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    sh """
                        # Login to ECR
                        aws ecr get-login-password --region ${AWS_REGION} | \\
                            docker login \\
                                --username AWS \\
                                --password-stdin ${env.ECR_REGISTRY}

                        # Push versioned tag
                        docker push ${env.IMAGE_FULL}

                        # Push latest tag (only on main branch)
                        if [ "${env.GIT_BRANCH_NAME}" = "main" ]; then
                            docker push ${env.IMAGE_LATEST}
                        fi
                    """
                }
                echo "Pushed: ${env.IMAGE_FULL}"
            }
        }

        // ----------------------------------------------------------
        // STAGE 6 — DEPLOY TO EKS (main branch only)
        // ----------------------------------------------------------
        stage('Deploy to EKS') {
            when {
                allOf {
                    branch 'main'
                    not { expression { params.SKIP_DEPLOY } }
                }
            }
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ],
                    file(credentialsId: 'kubeconfig-laulain', variable: 'KUBECONFIG')
                ]) {
                    sh """
                        # Refresh kubeconfig
                        aws eks update-kubeconfig \\
                            --name ${EKS_CLUSTER} \\
                            --region ${AWS_REGION}

                        # Apply K8s manifests (ConfigMaps, Services, Ingress)
                        kubectl apply -k k8s/overlays/prod \\
                            --namespace ${K8S_NAMESPACE}

                        # Update the deployment image
                        kubectl set image deployment/${K8S_DEPLOYMENT} \\
                            ${K8S_CONTAINER}=${env.IMAGE_FULL} \\
                            --namespace ${K8S_NAMESPACE}

                        # Record the deployment for audit trail
                        kubectl annotate deployment/${K8S_DEPLOYMENT} \\
                            --namespace ${K8S_NAMESPACE} \\
                            --overwrite \\
                            kubernetes.io/change-cause="Jenkins #${BUILD_NUMBER} | ${env.GIT_SHA} | \$(date -u +%Y-%m-%dT%H:%M:%SZ)"

                        # Wait for rollout to complete (timeout: 5 minutes)
                        kubectl rollout status deployment/${K8S_DEPLOYMENT} \\
                            --namespace ${K8S_NAMESPACE} \\
                            --timeout=300s
                    """
                }
            }
            post {
                failure {
                    // Auto-rollback on deployment failure
                    withCredentials([
                        file(credentialsId: 'kubeconfig-laulain', variable: 'KUBECONFIG')
                    ]) {
                        sh """
                            echo "Deployment failed — rolling back..."
                            kubectl rollout undo deployment/${K8S_DEPLOYMENT} \\
                                --namespace ${K8S_NAMESPACE}
                        """
                    }
                }
            }
        }

        // ----------------------------------------------------------
        // STAGE 7 — HEALTH CHECK
        // ----------------------------------------------------------
        stage('Health Check') {
            when {
                allOf {
                    branch 'main'
                    not { expression { params.SKIP_DEPLOY } }
                }
            }
            steps {
                withCredentials([
                    file(credentialsId: 'kubeconfig-laulain', variable: 'KUBECONFIG')
                ]) {
                    sh """
                        sleep 15

                        READY=\$(kubectl get deployment/${K8S_DEPLOYMENT} \\
                            --namespace ${K8S_NAMESPACE} \\
                            -o jsonpath='{.status.readyReplicas}')
                        DESIRED=\$(kubectl get deployment/${K8S_DEPLOYMENT} \\
                            --namespace ${K8S_NAMESPACE} \\
                            -o jsonpath='{.spec.replicas}')

                        echo "Ready: \$READY / Desired: \$DESIRED"

                        if [ "\${READY:-0}" != "\$DESIRED" ]; then
                            echo "Health check failed — not all replicas ready"
                            exit 1
                        fi

                        echo "All \$DESIRED replicas healthy"
                    """
                }
            }
        }

        // ----------------------------------------------------------
        // STAGE 8 — CLEANUP LOCAL IMAGES
        // ----------------------------------------------------------
        stage('Cleanup') {
            steps {
                sh """
                    docker rmi ${env.IMAGE_FULL} || true
                    docker rmi ${env.IMAGE_LATEST} || true
                    docker image prune -f --filter 'until=24h' || true
                """
            }
        }
    }

    post {
        success {
            echo """
            ╔══════════════════════════════════════════════╗
            ║   BUILD SUCCESSFUL                           ║
            ║   Build:   #${BUILD_NUMBER}                  ║
            ║   Branch:  ${env.GIT_BRANCH_NAME}            ║
            ║   Commit:  ${env.GIT_SHA}                    ║
            ║   Image:   ${env.IMAGE_TAG}                  ║
            ╚══════════════════════════════════════════════╝
            """
        }
        failure {
            echo "BUILD FAILED — Build #${BUILD_NUMBER} | ${env.GIT_SHA}"
        }
        always {
            cleanWs(
                cleanWhenSuccess: true,
                cleanWhenFailure: false
            )
        }
    }
}

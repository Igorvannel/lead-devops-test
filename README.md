# 🚀 @fric Lead DevOps – Pipeline CI/CD "Zero-to-Deploy"

> **Candidat :** Igor Vannel Sibemou Tientcheu  
> **Poste visé :** Lead DevOps — @fric Payment Solutions  
> **Stack :** Spring Boot 3.2 · GitHub Actions · Docker · SonarCloud · VPS Linux

---

## 📋 Table des matières

1. [Architecture du pipeline](#architecture-du-pipeline)  
2. [Choix techniques & justifications](#choix-techniques--justifications)  
3. [Structure du projet](#structure-du-projet)  
4. [Prérequis](#prérequis)  
5. [Configuration des Secrets CI/CD](#configuration-des-secrets-cicd)  
6. [Prérequis sur le serveur cible (VPS)](#prérequis-sur-le-serveur-cible-vps)  
7. [Déclenchement et suivi du pipeline](#déclenchement-et-suivi-du-pipeline)  
8. [Endpoints applicatifs](#endpoints-applicatifs)  
9. [Exécution locale](#exécution-locale)  

---

## Architecture du pipeline

```
Git Push (master)
       │
       ▼
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐     ┌─────────────────┐
│  JOB 1          │────▶│  JOB 2           │────▶│  JOB 3            │────▶│  JOB 4          │
│  Build & Test   │     │  SonarCloud      │     │  Docker Build     │     │  Deploy VPS     │
│  (Maven+JaCoCo) │     │  Code Analysis   │     │  & Push Docker Hub│     │  via SSH        │
└─────────────────┘     └──────────────────┘     └───────────────────┘     └─────────────────┘
                                                                                     │
                                                                                     ▼
                                                                         ┌─────────────────────┐
                                                                         │  JOB 5 – Notify     │
                                                                         │  GitHub Summary +   │
                                                                         │  Webhook (Slack)    │
                                                                         └─────────────────────┘
```

Chaque job est conditionnel : si un job échoue, les jobs suivants sont annulés. Le déploiement n'a lieu que sur la branche `master`/`main`.

---

## Choix techniques & justifications

| Décision | Choix | Justification |
|---|---|---|
| **CI/CD** | GitHub Actions | Zéro infrastructure supplémentaire, native au repo, visibilité immédiate via l'onglet Actions. Plus agile qu'un Jenkins standalone pour ce contexte. |
| **Analyse qualité** | SonarCloud | Version cloud de SonarQube — pas de serveur Sonar à maintenir, dashboard public, intégration GitHub native (Quality Gate visible sur chaque PR). |
| **Image Docker** | Multi-stage build | Build JDK séparé du runtime JRE. Image finale ~180MB vs ~600MB avec JDK complet. Moins de surface d'attaque. |
| **Sécurité container** | User non-root | Le container tourne avec un utilisateur `appuser` dédié, jamais en root. |
| **Layers Spring Boot** | `jarmode=layertools` | Décomposition du JAR en couches Docker pour un cache optimal lors des re-builds. |
| **Health check** | `/actuator/health` | Spring Actuator intégré, vérifié par Docker et le pipeline post-déploiement. |

---

## Structure du projet

```
lead-devops-test/
├── .github/
│   └── workflows/
│       └── ci-cd.yml              # Pipeline GitHub Actions complet
├── src/
│   ├── main/java/com/afric/hello/
│   │   ├── HelloWorldApplication.java
│   │   └── HelloController.java   # Endpoints /api/hello et /api/health
│   ├── main/resources/
│   │   └── application.properties
│   └── test/java/com/afric/hello/
│       └── HelloControllerTest.java  # Tests MockMvc
├── Dockerfile                     # Multi-stage build optimisé
├── sonar-project.properties       # Config SonarCloud
├── .dockerignore
├── .gitignore
├── pom.xml                        # Maven + JaCoCo + Sonar plugin
└── README.md
```

---

## Prérequis

### Outils locaux
- Java 17+
- Maven 3.8+ (ou utiliser `./mvnw`)
- Docker Desktop
- Git

### Comptes externes
- [GitHub](https://github.com) — hébergement du repo
- [Docker Hub](https://hub.docker.com) — registry de l'image
- [SonarCloud](https://sonarcloud.io) — analyse de code (gratuit pour projets publics)

---

## Configuration des Secrets CI/CD

Aller dans le repo GitHub → **Settings → Secrets and variables → Actions → New repository secret**

### Secrets obligatoires

| Secret | Description | Où l'obtenir |
|---|---|---|
| `DOCKERHUB_USERNAME` | Votre username Docker Hub | hub.docker.com → Account Settings |
| `DOCKERHUB_TOKEN` | Access Token Docker Hub (**pas le mot de passe**) | hub.docker.com → Security → New Access Token |
| `VPS_HOST` | IP du serveur cible | `192.99.42.107` |
| `VPS_USER` | Utilisateur SSH sur le VPS | Ex: `deploy` ou `ubuntu` |
| `VPS_SSH_PRIVATE_KEY` | Clé privée SSH (contenu du fichier `id_rsa`) | Voir section ci-dessous |
| `SONAR_TOKEN` | Token d'authentification SonarCloud | sonarcloud.io → My Account → Security |
| `SONAR_PROJECT_KEY` | Clé du projet SonarCloud | sonarcloud.io → Project → Project Information |
| `SONAR_ORGANIZATION` | Organisation SonarCloud | sonarcloud.io → Organization → Key |

### Secret optionnel

| Secret | Description |
|---|---|
| `WEBHOOK_URL` | URL Slack Incoming Webhook pour les notifications (optionnel) |

---

### 🔑 Génération et configuration des clés SSH

#### Sur votre machine locale, générer une paire de clés dédiée au déploiement :

```bash
# Générer une clé ED25519 dédiée (plus sécurisée que RSA)
ssh-keygen -t ed25519 -C "github-actions-deploy@afric" -f ~/.ssh/afric_deploy_key -N ""

# Afficher la clé PUBLIQUE → à copier sur le VPS
cat ~/.ssh/afric_deploy_key.pub

# Afficher la clé PRIVÉE → à copier dans le secret GitHub VPS_SSH_PRIVATE_KEY
cat ~/.ssh/afric_deploy_key
```

#### Sur le VPS, ajouter la clé publique :

```bash
# Se connecter au VPS
ssh root@192.99.42.107

# Créer l'utilisateur deploy (si pas encore fait)
useradd -m -s /bin/bash deploy
usermod -aG docker deploy

# Ajouter la clé publique
mkdir -p /home/deploy/.ssh
echo "COLLER_LA_CLÉ_PUBLIQUE_ICI" >> /home/deploy/.ssh/authorized_keys
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

#### Dans GitHub, ajouter la clé privée comme secret :
1. Copier **tout le contenu** de `~/.ssh/afric_deploy_key` (y compris les lignes `-----BEGIN...` et `-----END...`)
2. Coller dans le secret `VPS_SSH_PRIVATE_KEY`

---

## Prérequis sur le serveur cible (VPS)

### Installation de Docker sur le VPS (Ubuntu 22.04)

```bash
# Se connecter en root
ssh root@192.99.42.107

# Installer Docker
apt-get update
apt-get install -y ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin

# Démarrer et activer Docker
systemctl enable docker
systemctl start docker

# Vérifier
docker --version
```

### Ouvrir le port applicatif dans le firewall

```bash
# Avec ufw
ufw allow 8090/tcp
ufw reload

# Vérifier
ufw status
```

### Résumé des prérequis VPS

| Prérequis | Commande de vérification |
|---|---|
| Docker installé et actif | `docker --version && systemctl is-active docker` |
| Utilisateur `deploy` membre du groupe `docker` | `groups deploy` |
| Port 8090 ouvert | `ufw status` ou `ss -tlnp \| grep 8090` |
| Clé SSH publique configurée | `cat /home/deploy/.ssh/authorized_keys` |

---

## Déclenchement et suivi du pipeline

### Déclencher le pipeline

Le pipeline se déclenche **automatiquement** sur tout `push` vers `master` ou `main` :

```bash
# Cloner le repo
git clone https://github.com/VOTRE_USERNAME/lead-devops-test.git
cd lead-devops-test

# Faire une modification et pusher
git add .
git commit -m "feat: trigger CI/CD pipeline"
git push origin master
```

### Suivre l'exécution

1. Aller sur **GitHub → onglet Actions**
2. Cliquer sur le workflow en cours `CI/CD – Zero-to-Deploy`
3. Observer les 5 jobs s'enchaîner en temps réel

### Vérifier le déploiement

```bash
# Vérifier que le container tourne sur le VPS
ssh deploy@192.99.42.107 "docker ps | grep afric-hello-world"

# Tester l'API déployée
curl http://192.99.42.107:8090/api/hello
curl http://192.99.42.107:8090/api/health
curl http://192.99.42.107:8090/actuator/health

# Voir les logs du container
ssh deploy@192.99.42.107 "docker logs afric-hello-world --tail 50 -f"
```

---

## Endpoints applicatifs

| Endpoint | Méthode | Description |
|---|---|---|
| `/api/hello` | GET | Message de bienvenue avec version et timestamp |
| `/api/health` | GET | Health check applicatif custom |
| `/actuator/health` | GET | Health check Spring Actuator (utilisé par Docker) |
| `/actuator/info` | GET | Informations sur l'application |
| `/actuator/metrics` | GET | Métriques applicatives |

---

## Exécution locale

```bash
# Build et tests
./mvnw verify

# Lancer l'application
./mvnw spring-boot:run

# Build Docker local
docker build -t afric-hello-world:local .

# Run Docker local
docker run -d --name afric-local -p 8090:8090 afric-hello-world:local

# Test
curl http://localhost:8090/api/hello
```

---

*Pipeline conçu et implémenté par Igor Vannel Sibemou Tientcheu — Lead DevOps Candidate @fric Payment Solutions*

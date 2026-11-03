# Publishing to Maven Central

Three artifacts go out together, under the namespace `io.github.narcisbodea`:

- `smstunnel-java-parent` (the parent POM, needed by the other two),
- `smstunnel-java` (the client),
- `smstunnel-spring-boot-starter`.

GitHub Actions publishes them (`.github/workflows/release.yml`) when you publish a GitHub release tagged `v<version>`. It runs the tests, builds the sources and Javadoc jars, signs everything with your GPG key, uploads one bundle to the Central Portal and waits until Central has validated it. With `central.autoPublish=true` (in `pom.xml`) Central then publishes it by itself.

Maven Central has no Trusted Publishing (OIDC) for Maven builds: its Maven guide (checked 3 November 2026) uses a user token. So the release needs four secrets, which you create and store yourself, only in a protected GitHub environment. Nothing in this repository holds a secret, and nobody else needs your credentials.

Publish this repository **before** `smstunnel-keycloak`: the Keycloak extension's build downloads `smstunnel-java` from Maven Central.

## 1. Central Portal account (once)

1. Open <https://central.sonatype.com>, click **Sign In**, and choose **Continue with GitHub** with the account `narcisbodea`.
2. Open **Namespaces** (<https://central.sonatype.com/publishing/namespaces>). Signing in with GitHub gives you the namespace `io.github.narcisbodea`, already **Verified**. If it is missing: **Add Namespace** → `io.github.narcisbodea`, copy the verification key, create an empty **public** GitHub repository named exactly that key, click **Verify Namespace**, and delete the repository once it shows Verified.
3. Open <https://central.sonatype.com/usertoken> → **Generate User Token**, give it a name (for example `github-actions smstunnel`) and an expiration. Copy the **username** and the **password** it shows: they are shown only once.

## 2. A GPG key for signing (once, on your own computer)

Central accepts only signed files, and checks the signature against public key servers.

```bash
gpg --full-generate-key          # (1) RSA and RSA, 4096 bits; name "Narcis Bodea", email contact@narboweb.ro; a passphrase
gpg --list-secret-keys --keyid-format=long contact@narboweb.ro    # note the key id after "rsa4096/"
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
gpg --keyserver keys.openpgp.org --send-keys <KEY-ID>             # then confirm the email keys.openpgp.org sends you
gpg --armor --export-secret-keys <KEY-ID>                         # the private key, for the GitHub secret below
```

On Windows, Gpg4win installs `gpg`. Keep a backup of the key and its passphrase; the same key signs every later release and the Keycloak extension.

## 3. GitHub repository and environment (once)

1. Create the public repository `narcisbodea/smstunnel-java` and push this folder to `main`.
2. **Settings → Environments → New environment**, name it `maven-central`:
   - **Required reviewers**: yourself. Each release then waits for your **Review deployments → Approve**.
   - **Deployment branches and tags → Selected branches and tags → Add rule**: tag `v*`.
   - **Environment secrets** (**Add environment secret**, four times):

     | Name | Value |
     | --- | --- |
     | `CENTRAL_TOKEN_USERNAME` | the token username from step 1.3 |
     | `CENTRAL_TOKEN_PASSWORD` | the token password from step 1.3 |
     | `MAVEN_GPG_KEY` | the whole output of `gpg --armor --export-secret-keys <KEY-ID>`, `-----BEGIN PGP PRIVATE KEY BLOCK-----` lines included |
     | `MAVEN_GPG_PASSPHRASE` | the key's passphrase |

3. **Actions**: the **CI** workflow runs on every push and pull request (Java 11, 17, 21 and 25; Spring Boot 3.5 and 4.1).

## 4. Release 1.0.0

1. Check that `pom.xml` (all three) and the top of `CHANGELOG.md` say `1.0.0`, and that **CI** is green on `main`.
2. GitHub: **Releases → Draft a new release → Choose a tag**, type `v1.0.0` and **Create new tag on publish** (target `main`), title `1.0.0`, paste the changelog entry, **Publish release**.
3. **Actions → Release**: approve the `maven-central` deployment. The job checks that the tag matches the version, then runs `mvn -Prelease deploy` (tests, signatures, upload) and waits for Central's validation: about 2 to 10 minutes.
4. <https://central.sonatype.com/publishing/deployments> shows the deployment as **Publishing**, then **Published**. The files reach <https://repo1.maven.org/maven2/io/github/narcisbodea/> in about 15 to 30 minutes; search on central.sonatype.com can take a few hours.
5. Try it in an empty project with the dependency from the README.

To check the result before it goes public, set `<central.autoPublish>false</central.autoPublish>` in `pom.xml` for that release: the deployment then stops at **Validated**, and you click **Publish** (or **Drop**) on the Deployments page.

## Later versions

```bash
mvn versions:set -DnewVersion=1.0.1 -DgenerateBackupPoms=false   # all three POMs
# CHANGELOG.md: a new entry at the top
git commit -am "Release 1.0.1" && git push
```

Then publish a GitHub release tagged `v1.0.1`. A version can never be uploaded twice, so a fix always needs a new number.

## Before the first release: a dry run without uploading

With a throwaway key, nothing leaves your computer:

```bash
export GNUPGHOME=$(mktemp -d)
printf '%%no-protection\nKey-Type: eddsa\nKey-Curve: ed25519\nName-Real: Dry run\nName-Email: dry-run@example.invalid\nExpire-Date: 1d\n%%commit\n' \
  | gpg --batch --gen-key
export MAVEN_GPG_KEY="$(gpg --armor --export-secret-keys dry-run@example.invalid)" MAVEN_GPG_PASSPHRASE=""
mvn -Prelease -DskipTests verify      # every jar and POM gets a .asc next to it in target/
rm -rf "$GNUPGHOME"
```

This was run for 1.0.0 (in `maven:3.9-eclipse-temurin-21`): Javadoc, sources and all 9 signatures (3 POMs, 6 jars) were produced.

## If the release fails

- `401` from Central: the token username or password secret is wrong or expired; make a new token (step 1.3).
- `Invalid signature` / `Could not find a public key`: the public key is not on a key server yet; send it (step 2) and run the workflow again (**Re-run jobs**).
- `Component with package url ... already exists`: that version is already on Central. Raise the version.
- `Namespace ... is not allowed`: the namespace is not verified for the token's account (step 1.2).
- The job never starts: approve the deployment, or check the environment's `v*` tag rule.

## What stays manual

Only the steps above: the Central account and token, the GPG key, the GitHub repository with its environment and secrets, and publishing a release.

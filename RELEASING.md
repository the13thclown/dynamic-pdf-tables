# Releasing to Maven Central

Publishing goes through the Sonatype Central Portal (https://central.sonatype.com). The
`release` Maven profile builds sources + javadoc jars, signs everything with GPG and uploads
the bundle via the `central-publishing-maven-plugin`.

## One-time setup

### 1. Central Portal account and namespace

1. Go to https://central.sonatype.com and **sign in with GitHub**.
2. Under *Namespaces*, the namespace `io.github.the13thclown` is granted automatically when
   you sign in via the matching GitHub account (verify it shows as *Verified*).
3. Generate a **user token**: account icon → *View Account* → *Generate User Token*. This
   yields a username/password pair (not your login credentials).

### 2. Maven credentials

Put the token into `~/.m2/settings.xml` under the server id `central` (matches
`publishingServerId` in the pom):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- token username --></username>
      <password><!-- token password --></password>
    </server>
  </servers>
</settings>
```

### 3. GPG key

```
gpg --gen-key                                        # RSA, your name + email
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
```

Central verifies signatures against public keyservers; `keyserver.ubuntu.com` is one it
checks. The gpg plugin runs with `bestPractices` enabled, so the passphrase is **not** read
from settings.xml — export it as an environment variable for the release shell:

```powershell
$env:MAVEN_GPG_PASSPHRASE = "..."
```

## Cutting a release

1. Make sure `master` is green and the working tree is clean.
2. Set the release version in `pom.xml` (drop `-SNAPSHOT`), update the version in the
   README's dependency snippet, and commit (`Release X.Y.Z`).
3. Deploy:

   ```powershell
   mvn -Prelease clean deploy
   ```

   The plugin uploads the bundle and waits until Central reports it **validated**
   (`waitUntil=validated`, `autoPublish=false` — nothing is public yet).
4. On https://central.sonatype.com/publishing check the deployment, then press **Publish**.
   Propagation to Maven Central search takes up to a couple of hours.
5. Tag and start the next cycle:

   ```powershell
   git tag vX.Y.Z
   git push origin master --tags
   ```

   Bump `pom.xml` to the next `-SNAPSHOT` version and commit (`Start X.Y+1.0-SNAPSHOT`).

## After the first release: PDFBox FAQ listing

The Apache PDFBox FAQ answers "Can I use PDFBox to create complex layouts?" with a
list of table/layout libraries built on PDFBox — the main discovery channel for this
niche. Once 0.2.0 is public on Maven Central, submit a PR to the docs mirror:

1. Fork https://github.com/apache/pdfbox-docs and edit `content/3.0/faq.md`
   (3.0 only — this library requires PDFBox 3.x).
2. In the list under "But PDFBox is the foundation of some projects which might
   help", the entries are alphabetical; insert after BoxTable:

   ```markdown
   - [dynamic-pdf-tables](https://github.com/the13thclown/dynamic-pdf-tables)
   ```

3. Open the PR with a short note: dynamic table building on PDFBox 3.x with
   element-granular pagination (cells split across pages), nested tables,
   repeating headers and Apache-2.0 licensing; released on Maven Central as
   `io.github.the13thclown:dynamic-pdf-tables`.

## Local dry run

Everything except the upload can be verified without credentials or a GPG key:

```powershell
mvn -Prelease verify "-Dgpg.skip=true"
```

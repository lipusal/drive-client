<html>
<title>drive-cmdline-sample</title>
<body>
  <h2>Instructions for the Drive API V1.0 Command-Line Sample</h2>

  <h3>Browse Online</h3>

  <ul>
    <li><a
      href="http://code.google.com/p/google-api-java-client/source/browse?repo=samples#hg/drive-cmdline-sample">Browse
        Source</a>, or main file <a
      href="http://code.google.com/p/google-api-java-client/source/browse/drive-cmdline-sample/src/main/java/com/google/api/services/samples/drive/cmdline/DriveSample.java?repo=samples">DriveSample.java</a></li>
  </ul>

  <h3>Register Your Application</h3>
  
  <ul>
    <li>Visit the <a href="https://cloud.google.com/console/start/api?id=drive">Google Cloud
        console</a>. 
    </li>
    <li>If necessary, sign in to your Google Account, select or create a project,
        and agree to the terms of service.  Click Continue.</li>
    <li>Select "Installed application" and choose type "Other" under the Installed Application type.</li>
    <li>Within "OAuth 2.0 Client ID", click on "Download JSON". Later on, after you check
        out the sample project, you will copy this downloaded file (e.g. 
        <code>~/Downloads/client_secrets.json</code>) to
        <a href="src/main/resources/client_secrets.json">src/main/resources/client_secrets.json</a>.
        If you skip this step, when trying to run the sample you will get a <code>400 
        INVALID_CLIENT</code> error in the browser.
    </li>
  </ul>

  <h3>Checkout Instructions</h3>

  <p>
    <b>Prerequisites:</b> install <a href="http://java.com">Java 6</a>, <a
      href="http://mercurial.selenic.com/">Mercurial</a> and <a
      href="http://maven.apache.org/download.html">Maven</a>. You may need to
    set your
    <code>JAVA_HOME</code>
    .
  </p>

  <pre>
    <code>cd <i>[someDirectory]</i>
hg clone https://code.google.com/p/google-api-java-client.samples/ google-api-java-client-samples
cd google-api-java-client-samples/drive-cmdline-sample
cp ~/Downloads/client_secrets.json src/main/resources/client_secrets.json
mvn compile
mvn -q exec:java
</code>
  </pre>

  <p>To enable logging of HTTP requests and responses (highly recommended
    when developing), please take a look at <a href="logging.properties">logging.properties</a>.</p>

  <h3>Setup Project</h3>

  <p>
    <b>Prerequisites:</b> install <a href="http://www.eclipse.org/downloads/">Eclipse</a>,
    the <a href="http://javaforge.com/project/HGE">Mercurial plugin</a>, and the
    <a href="http://m2eclipse.sonatype.org/installing-m2eclipse.html">Maven
      plugin</a>.
  </p>

  <ul>
    <li>Setup Eclipse Preferences
      <ul>
        <li>Window &gt; Preferences... (or on Mac, Eclipse &gt;
          Preferences...)</li>
        <li>Select Maven
          <ul>
            <li>check on "Download Artifact Sources"</li>
            <li>check on "Download Artifact JavaDoc"</li>
          </ul>
        </li>
      </ul>
    </li>
    <li>Import <code>drive-cmdline-sample</code> project
      <ul>
        <li>File &gt; Import...</li>
        <li>Select "General &gt; Existing Project into Workspace" and click
          "Next"</li>
        <li>Click "Browse" next to "Select root directory", find <code>
            <i>[someDirectory]</i>/google-api-java-client-samples/drive-cmdline-sample
          </code> and click "Next"
        </li>
        <li>Click "Finish"</li>
      </ul>
    </li>
    <li>Run
      <ul>
        <li>Right-click on project drive-cmdline-sample</li>
        <li>Run As &gt; Java Application</li>
        <li>If asked, type "DriveSample" and click OK</li>
      </ul>
    </li>
  </ul>

</body>
</html>

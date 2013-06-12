package org.korosoft.jenkins.plugin.rtp;

/*

The New BSD License

Copyright (c) 2011-2013, Dmitry Korotkov
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

- Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or
  other materials provided with the distribution.

- Neither the name of the Jenkins RuSalad Plugin nor the names of its
  contributors may be used to endorse or promote products derived from this
  software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich text publisher
 *
 * @author Dmitry Korotkov
 * @since 1.0
 */
public class RichTextPublisher extends Recorder {

    private static final transient Pattern FILE_VAR_PATTERN = Pattern.compile("\\$\\{(file|file_sl):([^\\}]+)\\}", Pattern.CASE_INSENSITIVE);
    private String stableText;
    private String unstableText;
    private String failedText;
    private Boolean unstableAsStable = true;
    private Boolean failedAsStable = true;

    @DataBoundConstructor
    public RichTextPublisher(String stableText, String unstableText, String failedText, Boolean unstableAsStable, Boolean failedAsStable) {
        this.stableText = stableText;
        this.unstableText = unstableText;
        this.failedText = failedText;
        this.unstableAsStable = unstableAsStable == null ? true: unstableAsStable;
        this.failedAsStable = failedAsStable == null ? true: failedAsStable;
    }

    public String getStableText() {
        return stableText;
    }

    public void setStableText(String stableText) {
        this.stableText = stableText;
    }

    public String getUnstableText() {
        return unstableText;
    }

    public void setUnstableText(String unstableText) {
        this.unstableText = unstableText;
    }

    public String getFailedText() {
        return failedText;
    }

    public void setFailedText(String failedText) {
        this.failedText = failedText;
    }

    public boolean isUnstableAsStable() {
        return unstableAsStable;
    }

    public void setUnstableAsStable(boolean unstableAsStable) {
        this.unstableAsStable = unstableAsStable;
    }

    public boolean isFailedAsStable() {
        return failedAsStable;
    }

    public void setFailedAsStable(boolean failedAsStable) {
        this.failedAsStable = failedAsStable;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final String text;
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            text = stableText;
        } else if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
            text = unstableAsStable ? stableText : unstableText;
        } else {
            text = failedAsStable ? stableText : failedText;
        }

        Map<String, String> vars = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : build.getEnvironment(listener).entrySet()) {
            vars.put(String.format("ENV:%s", entry.getKey()), entry.getValue());
        }
        vars.putAll(build.getBuildVariables());

        Matcher matcher = FILE_VAR_PATTERN.matcher(text);
        int start = 0;
        while (matcher.find(start)) {
            String fileName = matcher.group(2);
            FilePath filePath = new FilePath(build.getWorkspace(), fileName);
            if (filePath.exists()) {
                String value = filePath.readToString();
                if (matcher.group(1).length() != 4) { // Group is file_sl
                    value = value.replace("\n", "").replace("\r", "");
                }
                vars.put(String.format("%s:%s", matcher.group(1), fileName), value);
            }
            start = matcher.end();
        }

        AbstractRichTextAction action = new BuildRichTextAction(build, replaceVars(text, vars));
        build.addAction(action);
        build.save();

        return true;
    }

    private String replaceVars(String publishText, Map<String, String> vars) {
        for (Map.Entry<String, String> var : vars.entrySet()) {
            String key = String.format("${%s}", var.getKey());
            String value = var.getValue();
            publishText = publishText.replace(key, value);
        }
        return publishText;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new ProjectRichTextAction(project, stableText));
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public FormValidation doCheckPublishText(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, ServletException {
            try {
                FilePath workspace = project.getSomeWorkspace();
                if (workspace == null) {
                    return FormValidation.warning("Cannot validate the path since the project was never built.");
                }
                Matcher matcher = FILE_VAR_PATTERN.matcher(value);
                int start = 0;
                List<String> missingFiles = new ArrayList<String>();
                while (matcher.find(start)) {
                    String fileName = matcher.group(2);
                    FilePath filePath = new FilePath(workspace, fileName);
                    if (!filePath.exists()) {
                        missingFiles.add(fileName);
                    }
                    start = matcher.end();
                }
                if (missingFiles.isEmpty()) {
                    return FormValidation.ok();
                }
                if (missingFiles.size() == 1) {
                    return FormValidation.warning("File %s was not found in the workspace", missingFiles.get(0));
                }
                return FormValidation.warning("Files %s were not found in the workspace", StringUtils.join(missingFiles, ", "));
            } catch (InterruptedException e) {
                return FormValidation.error(e, "Interrupted");
            }
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Publish rich text message";
        }

    }
}

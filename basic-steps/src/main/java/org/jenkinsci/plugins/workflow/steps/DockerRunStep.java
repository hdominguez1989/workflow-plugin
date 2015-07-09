/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.workflow.steps;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSimpleTemplate;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import com.nirima.jenkins.plugins.docker.builder.DockerBuilderControlCloudOption;
import hudson.*;
import hudson.model.*;

import java.util.logging.Level;

import java.io.InputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import com.github.dockerjava.api.DockerClient;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import javax.annotation.Nonnull;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import com.google.inject.Inject;
import com.nirima.jenkins.plugins.docker.builder.DockerBuilderControlOptionRun;
import org.kohsuke.stapler.export.Exported;


/**
 * Simple email sender step.
 * 
 * @author <a href="mailto:hdominguez@stratio.com">hdominguez@stratio.com</a>
 */
public class DockerRunStep extends AbstractStepImpl implements Serializable {
	@DataBoundSetter
	public String dnsString;
	@DataBoundSetter
	public String cloudName;
	@DataBoundSetter
	public String image;
	@DataBoundSetter
	public String dockerCommand;
	@DataBoundSetter
	public String volumesString;
	@DataBoundSetter
	public String volumesFrom;
	@DataBoundSetter
	public String environmentsString;
	@DataBoundSetter
	public String lxcConfString;
	@DataBoundSetter
	public boolean privileged = false;
	@DataBoundSetter
	public boolean tty = false;
	@DataBoundSetter
	public String hostname;
	@DataBoundSetter
	public String bindPorts;
	@DataBoundSetter
	public Integer memoryLimit;
	@DataBoundSetter
	public Integer cpuShares;
	@DataBoundSetter
	public boolean bindAllPorts = false;
	@DataBoundSetter
	public String macAddress;

	public void setDnsString(String dnsString) {
		if(dnsString == null){
			this.dnsString = "";
		}
	}
	public void setVolumesString(String volumesString) {
		if(volumesString == null){
			this.volumesString = "";
		}
	}
	public void setVolumesFrom(String volumesFrom) {
		if(volumesFrom == null){
			this.volumesFrom = "";
		}
	}
	public void setEnvironmentsString(String environmentsString) {
		if(environmentsString == null){
			this.environmentsString = "";
		}
	}

	public void setCpuShares(Integer cpuShares) {
		if(cpuShares == null){
			//Default value
			this.cpuShares = 0;
		}
	}
	@DataBoundConstructor
	public DockerRunStep(@Nonnull String cloudName, @Nonnull String image) {
		this.cloudName = cloudName;
		this.image = image;
	}

	@Extension
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(DockerRunStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "docker_run";
		}

		@Override
		public String getDisplayName() {
			return "DockerRun";
		}

	}

	public static class DockerRunStepExecution extends
			AbstractSynchronousStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		@StepContextParameter private transient WorkflowRun run;
		@StepContextParameter private transient FilePath workspace;
		@StepContextParameter private transient TaskListener listener;
		@StepContextParameter private transient Launcher launcher;

		@Inject
		private transient DockerRunStep step;

		@Override
		protected Void run() throws Exception {
			DockerCloud cloud = null;
			Node node = workspace.toComputer().getNode();
			if( node instanceof DockerSlave) {
				DockerSlave dockerSlave = (DockerSlave)node;
				cloud = dockerSlave.getCloud();
			}
			if( !Strings.isNullOrEmpty(step.cloudName) ) {
				cloud = (DockerCloud) Jenkins.getInstance().getCloud(step.cloudName);
			}
			if( cloud == null ) {
				throw new RuntimeException("Cannot list cloud for docker action");
			}
			DockerClient client = cloud.connect();
			System.out.println("Pulling image " + step.image);
			InputStream result = client.pullImageCmd(step.image).exec();
			String strResult = IOUtils.toString(result);
			System.out.println( "Pull result = "+ strResult);

			System.out.println("Starting container for image " + step.image);
			System.out.println("Checking for null values");

			step.setDnsString(step.dnsString);
			step.setEnvironmentsString(step.environmentsString);
			step.setVolumesFrom(step.volumesFrom);
			step.setVolumesString(step.volumesString);
			step.setCpuShares(step.cpuShares);
			DockerTemplateBase template = new DockerSimpleTemplate(step.image,
					step.dnsString, step.dockerCommand,
					step.volumesString, step.volumesFrom, step.environmentsString, step.lxcConfString, step.hostname,
					step.memoryLimit, step.cpuShares, step.bindPorts, step.bindAllPorts, step.privileged, step.tty, step.macAddress);
			String containerId = template.provisionNew(client);
			getLaunchAction(run).started(client, containerId);
			String aux = client.inspectContainerCmd(containerId).exec().getNetworkSettings().getIpAddress();
			System.out.println("ContainerIP :"  + aux);
			return null;
		}

		protected DockerLaunchAction getLaunchAction(Run<?, ?> build) {
			List<DockerLaunchAction> launchActionList = build.getActions(DockerLaunchAction.class);
			DockerLaunchAction launchAction;
			if( launchActionList.size() > 0 ) {
				launchAction = launchActionList.get(0);
			} else {
				launchAction = new DockerLaunchAction();
				build.addAction(launchAction);
			}
			return launchAction;
		}
	}

}

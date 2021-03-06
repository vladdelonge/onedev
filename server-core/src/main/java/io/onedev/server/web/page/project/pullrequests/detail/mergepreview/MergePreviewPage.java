package io.onedev.server.web.page.project.pullrequests.detail.mergepreview;

import java.io.Serializable;
import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.Sets;

import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;
import io.onedev.server.model.support.pullrequest.MergePreview;
import io.onedev.server.util.diff.WhitespaceOption;
import io.onedev.server.web.behavior.WebSocketObserver;
import io.onedev.server.web.component.commit.status.CommitStatusPanel;
import io.onedev.server.web.component.diff.revision.RevisionDiffPanel;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;
import io.onedev.server.web.page.project.commits.CommitDetailPage;
import io.onedev.server.web.page.project.pullrequests.detail.PullRequestDetailPage;
import io.onedev.server.web.util.EditParamsAware;

@SuppressWarnings("serial")
public class MergePreviewPage extends PullRequestDetailPage implements EditParamsAware {

	private static final String PARAM_WHITESPACE_OPTION = "whitespace-option";
	
	private static final String PARAM_PATH_FILTER = "path-filter";
	
	private static final String PARAM_BLAME_FILE = "blame-file";
	
	private State state = new State();
	
	public MergePreviewPage(PageParameters params) {
		super(params);

		state.pathFilter = params.get(PARAM_PATH_FILTER).toString();
		state.whitespaceOption = WhitespaceOption.ofNullableName(params.get(PARAM_WHITESPACE_OPTION).toString());
		state.blameFile = params.get(PARAM_BLAME_FILE).toString();
	}
	
	@Override
	protected String getRobotsMeta() {
		return "noindex,nofollow";
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();

		newContent(null);
	}

	public static PageParameters paramsOf(PullRequest request, State state) {
		PageParameters params = PullRequestDetailPage.paramsOf(request);

		if (state.whitespaceOption != WhitespaceOption.DEFAULT)
			params.set(PARAM_WHITESPACE_OPTION, state.whitespaceOption.name());
		if (state.pathFilter != null)
			params.set(PARAM_PATH_FILTER, state.pathFilter);
		if (state.blameFile != null)
			params.set(PARAM_BLAME_FILE, state.blameFile);
			
		return params;
	}
	
	@Override
	protected void onPopState(AjaxRequestTarget target, Serializable data) {
		super.onPopState(target, data);

		state = (State) data;
		
		newContent(target);
	}
	
	private void newContent(IPartialPageRequestHandler handler) {
		Fragment fragment;
		MergePreview preview = getPullRequest().getMergePreview();
		if (getPullRequest().isOpen() && preview != null && preview.getMergeCommitHash() != null) {
			fragment = new Fragment("content", "availableFrag", this);

			CommitDetailPage.State commitState = new CommitDetailPage.State();
			commitState.revision = preview.getTargetHeadCommitHash();
			PageParameters params = CommitDetailPage.paramsOf(projectModel.getObject(), commitState);
			fragment.add(new ViewStateAwarePageLink<Void>("targetHead", CommitDetailPage.class, params));
			
			commitState = new CommitDetailPage.State();
			commitState.revision = preview.getMergeCommitHash();
			params = CommitDetailPage.paramsOf(projectModel.getObject(), commitState);
			fragment.add(new ViewStateAwarePageLink<Void>("mergedCommit", CommitDetailPage.class, params));
			
			fragment.add(new CommitStatusPanel("buildStatus", ObjectId.fromString(preview.getMergeCommitHash()), null) {

				@Override
				protected Project getProject() {
					return MergePreviewPage.this.getProject();
				}

				@Override
				protected PullRequest getPullRequest() {
					return MergePreviewPage.this.getPullRequest();
				}
				
			});
			fragment.add(new WebMarkupContainer("outDated") {

				@Override
				protected void onInitialize() {
					super.onInitialize();
					
					add(new AjaxLink<Void>("link") {

						@Override
						public void onClick(AjaxRequestTarget target) {
							newContent(target);
						}
						
					});
					
					add(new WebSocketObserver() {

						@Override
						public Collection<String> getObservables() {
							return Sets.newHashSet(PullRequest.getWebSocketObservable(getPullRequest().getId()));
						}

						@Override
						public void onObservableChanged(IPartialPageRequestHandler handler) {
							handler.add(component);
						}
						
					});
				}
				
				@Override
				protected void onConfigure() {
					super.onConfigure();
					MergePreview latestPreview = getPullRequest().getMergePreview();
					setVisible(!getPullRequest().isOpen() 
							|| latestPreview == null 
							|| latestPreview.getMergeCommitHash() == null 
							|| !latestPreview.getTargetHeadCommitHash().equals(preview.getTargetHeadCommitHash())
							|| !latestPreview.getMergeCommitHash().equals(preview.getMergeCommitHash()));
				}

			}.setOutputMarkupPlaceholderTag(true));
			
			IModel<String> blameModel = new IModel<String>() {

				@Override
				public void detach() {
				}

				@Override
				public String getObject() {
					return state.blameFile;
				}

				@Override
				public void setObject(String object) {
					state.blameFile = object;
					pushState(RequestCycle.get().find(AjaxRequestTarget.class));
				}
				
			};
			IModel<String> pathFilterModel = new IModel<String>() {

				@Override
				public void detach() {
				}

				@Override
				public String getObject() {
					return state.pathFilter;
				}

				@Override
				public void setObject(String object) {
					state.pathFilter = object;
					pushState(RequestCycle.get().find(AjaxRequestTarget.class));
				}
				
			};
			IModel<WhitespaceOption> whitespaceOptionModel = new IModel<WhitespaceOption>() {

				@Override
				public void detach() {
				}

				@Override
				public WhitespaceOption getObject() {
					return state.whitespaceOption;
				}

				@Override
				public void setObject(WhitespaceOption object) {
					state.whitespaceOption = object;
					pushState(RequestCycle.get().find(AjaxRequestTarget.class));
				}

			};
			
			Component revisionDiff = new RevisionDiffPanel("revisionDiff", projectModel,  
					requestModel, preview.getTargetHeadCommitHash(), preview.getMergeCommitHash(), pathFilterModel, 
					whitespaceOptionModel, blameModel, null);
			revisionDiff.setOutputMarkupId(true);
			fragment.add(revisionDiff);
		} else {
			fragment = new Fragment("content", "notAvailableFrag", this);
			fragment.add(new WebSocketObserver() {
				
				@Override
				public void onObservableChanged(IPartialPageRequestHandler handler) {
					newContent(handler);
				}
				
				@Override
				public Collection<String> getObservables() {
					return Sets.newHashSet(PullRequest.getWebSocketObservable(getPullRequest().getId()));
				}
				
			});
		}
		fragment.setOutputMarkupId(true);
		
		if (handler != null) {
			replace(fragment);
			handler.add(fragment);
		} else {
			add(fragment);
		}
	}
	
	public State getState() {
		return state;
	}
	
	private void pushState(IPartialPageRequestHandler partialPageRequestHandler) {
		PageParameters params = paramsOf(getPullRequest(), state);
		CharSequence url = RequestCycle.get().urlFor(MergePreviewPage.class, params);
		pushState(partialPageRequestHandler, url.toString(), state);
	}
	
	@Override
	public PageParameters getParamsBeforeEdit() {
		return paramsOf(getPullRequest(), state);
	}

	@Override
	public PageParameters getParamsAfterEdit() {
		return paramsOf(getPullRequest(), state);
	}
	
	public static class State implements Serializable {

		private static final long serialVersionUID = 1L;

		public WhitespaceOption whitespaceOption = WhitespaceOption.DEFAULT;
		
		@Nullable
		public String pathFilter;
		
		@Nullable
		public String blameFile;
		
	}

}

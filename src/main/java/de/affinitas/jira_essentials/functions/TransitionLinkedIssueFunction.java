package de.affinitas.jira_essentials.functions;

        import java.util.Collection;
        import java.util.Iterator;
        import java.util.Map;

        import com.atlassian.jira.component.ComponentAccessor;
        import com.atlassian.jira.issue.Issue;
        import com.atlassian.jira.issue.MutableIssue;
        import com.atlassian.jira.issue.util.IssueChangeHolder;
        import com.atlassian.jira.util.ErrorCollection;
        import com.atlassian.jira.util.ImportUtils;
        import com.atlassian.jira.bc.issue.IssueService;
        import com.atlassian.jira.util.JiraUtils;
        import com.atlassian.jira.plugin.ComponentClassManager;
        import com.atlassian.jira.workflow.*;
        import com.opensymphony.module.propertyset.PropertySet;
        import com.opensymphony.workflow.WorkflowException;
        import com.opensymphony.workflow.loader.ActionDescriptor;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;

public class TransitionLinkedIssueFunction extends AbstractPreserveChangesPostFunction {
    private Logger log = LoggerFactory.getLogger(TransitionLinkedIssueFunction.class);
    public static final String TRANSITION = "transition";

    private static JiraWorkflow getWorkflow(Issue issue) {
        // get current workflow
        WorkflowManager wm = ComponentAccessor.getWorkflowManager();
        return wm.getWorkflow(issue);
    }

    private static ActionDescriptor transitionFromName(Issue issue, String name) {
        Collection<ActionDescriptor> actions = getWorkflow(issue).getAllActions();
        for (ActionDescriptor ad : actions)
            if (ad.getName().equals(name))
                return ad;
        return null;
    }

    private static ActionDescriptor transitionFromId(Issue issue, int id) {
        Collection<ActionDescriptor> actions = getWorkflow(issue).getAllActions();
        for (ActionDescriptor ad : actions)
            if (ad.getId() ==id)
                return ad;
        return null;
    }

    private void printAnyErrors(Issue subtask, ErrorCollection errorCollection) {
        if (errorCollection.hasAnyErrors()) {
            log.warn("Field validation error auto-transitioning parent issue " + subtask.getKey() + ":");
            Iterator iter = errorCollection.getErrorMessages().iterator();
            while (iter.hasNext()) {
                String errMsg = (String) iter.next();
                log.warn("\t" + errMsg);
            }
            iter = errorCollection.getErrors().keySet().iterator();
            while (iter.hasNext()) {
                String fieldName = (String) iter.next();
                log.warn("\tField " + fieldName + ": " + errorCollection.getErrors().get(fieldName));
            }
        }

    }


    public void executeFunction(Map transientVars, Map args, PropertySet ps, IssueChangeHolder holder) throws WorkflowException {
        MutableIssue issue = getIssue(transientVars);
        String transitionName = (String) args.get(TRANSITION);
        boolean indexingPreviouslyEnabled = ImportUtils.isIndexIssues();

        try {
            MutableIssue parentIssue = (MutableIssue) issue.getParentObject();
            if (parentIssue != null) {
                ActionDescriptor transition;
                // try to convert transitionName into an int
                try {
                    Integer transitionId = Integer.parseInt(transitionName);
                    transition = transitionFromId(parentIssue, transitionId);
                } catch (NumberFormatException e) {
                    //not an int - must be a transition name
                    transition = transitionFromName(parentIssue, transitionName);
                }

                if (transition == null) {
                    log.warn("Error while executing function : transition [" + transitionName + "] not found");
                    return;
                }

                if (!indexingPreviouslyEnabled)
                    ImportUtils.setIndexIssues(true);

                WorkflowTransitionUtil workflowTransitionUtil = (WorkflowTransitionUtil) JiraUtils.loadComponent(WorkflowTransitionUtilImpl.class);
                workflowTransitionUtil.setIssue(parentIssue);
                workflowTransitionUtil.setUserkey(this.getCallerUser(transientVars, args).getKey());
                workflowTransitionUtil.setAction(transition.getId());

                // validate and transition issue
                ErrorCollection errorCollection = workflowTransitionUtil.validate();
                printAnyErrors(parentIssue, errorCollection);

                if (!errorCollection.hasAnyErrors()) {
                    workflowTransitionUtil.progress();
                    ComponentAccessor.getIssueIndexManager().reIndex(parentIssue);
                }
            }
        } catch (Exception e) {
            log.warn("Error while executing function : " + e, e);
        } finally {
            if (!indexingPreviouslyEnabled)
                ImportUtils.setIndexIssues(false);
        }
    }
}
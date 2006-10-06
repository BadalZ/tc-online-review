/*
 * Copyright (C) 2006 TopCoder Inc.  All Rights Reserved.
 */
package com.cronos.onlinereview.actions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;
import org.apache.struts.upload.FormFile;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.DynaValidatorForm;

import com.cronos.onlinereview.autoscreening.management.ResponseSeverity;
import com.cronos.onlinereview.autoscreening.management.ScreeningManager;
import com.cronos.onlinereview.autoscreening.management.ScreeningResult;
import com.cronos.onlinereview.autoscreening.management.ScreeningTask;
import com.cronos.onlinereview.external.ExternalUser;
import com.cronos.onlinereview.external.UserRetrieval;
import com.topcoder.management.deliverable.Deliverable;
import com.topcoder.management.deliverable.Submission;
import com.topcoder.management.deliverable.SubmissionStatus;
import com.topcoder.management.deliverable.Upload;
import com.topcoder.management.deliverable.UploadManager;
import com.topcoder.management.deliverable.UploadStatus;
import com.topcoder.management.deliverable.UploadType;
import com.topcoder.management.deliverable.search.SubmissionFilterBuilder;
import com.topcoder.management.deliverable.search.UploadFilterBuilder;
import com.topcoder.management.phase.PhaseManager;
import com.topcoder.management.project.Project;
import com.topcoder.management.project.ProjectManager;
import com.topcoder.management.resource.Notification;
import com.topcoder.management.resource.Resource;
import com.topcoder.management.resource.ResourceManager;
import com.topcoder.management.resource.search.NotificationFilterBuilder;
import com.topcoder.management.review.ReviewManagementException;
import com.topcoder.management.review.ReviewManager;
import com.topcoder.management.review.data.Comment;
import com.topcoder.management.review.data.Review;
import com.topcoder.management.scorecard.ScorecardManager;
import com.topcoder.management.scorecard.data.Scorecard;
import com.topcoder.management.scorecard.data.ScorecardType;
import com.topcoder.project.phases.Phase;
import com.topcoder.search.builder.filter.AndFilter;
import com.topcoder.search.builder.filter.EqualToFilter;
import com.topcoder.search.builder.filter.Filter;
import com.topcoder.search.builder.filter.InFilter;
import com.topcoder.servlet.request.FileUpload;
import com.topcoder.servlet.request.FileUploadResult;
import com.topcoder.servlet.request.UploadedFile;
import com.topcoder.util.config.ConfigManagerException;
import com.topcoder.util.errorhandling.BaseException;
import com.topcoder.util.file.DocumentGenerator;
import com.topcoder.util.file.Template;

/**
 * This class contains Struts Actions that are meant to deal with Project's details. There are
 * following Actions defined in this class:
 * <ul>
 * <li>View Project Details</li>
 * <li>Contact Manager</li>
 * <li>Upload Submission</li>
 * <li>Download Submission</li>
 * <li>Upload Final Fix</li>
 * <li>Download Final Fix</li>
 * <li>Upload Test Case</li>
 * <li>Download Test Case</li>
 * <li>Download Document</li>
 * <li>Delete Submission</li>
 * <li>View Auto Screening</li>
 * </ul>
 * <p>
 * This class is thread-safe as it does not contain any mutable inner state.
 * </p>
 *
 * @author George1
 * @author real_vg
 * @version 1.0
 */
public class ProjectDetailsActions extends DispatchAction {

    /**
     * Creates a new instance of the <code>ProjectDetailsActions</code> class.
     */
    public ProjectDetailsActions() {
    }

    /**
     * This method is an implementation of &quot;View project Details&quot; Struts Action defined
     * for this assembly, which is supposed to gather all possible information about the project and
     * display it to user.
     *
     * @return an action forward to the appropriate page. If no error has occured, the forward will
     *         be to viewProjectDetails.jsp page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     */
    public ActionForward viewProjectDetails(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectProjectId(mapping, request, Constants.VIEW_PROJECT_DETAIL_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Retrieve a review to view
        Project project = verification.getProject();
        // Get Message Resources used for this request
        MessageResources messages = getResources(request);

        // Retrieve some basic project info (such as icons' names) and place it into request
        ActionsHelper.retrieveAndStoreBasicProjectInfo(request, project, messages);

        // Place a string that represents "my" current role(s) into the request
        ActionsHelper.retrieveAndStoreMyRole(request, getResources(request));
        // Obtain an array of "my" resources
        Resource[] myResources = (Resource[]) request.getAttribute("myResources");
        // Place an information about the amount of "my" payment into the request
        request.setAttribute("myPayment", ActionsHelper.determineMyPayment(myResources));
        // Place an information about my payment status into the request
        request.setAttribute("wasPaid", ActionsHelper.determineMyPaymentPaid(myResources));

        // Obtain an instance of Phase Manager
        PhaseManager phaseMgr = ActionsHelper.createPhaseManager(request);

        // Calculate the date when this project is supposed to end
        com.topcoder.project.phases.Project phProj = phaseMgr.getPhases(project.getId());
        phProj.calcEndDate();
        // Get all phases for the current project
        Phase[] phases = phProj.getAllPhases();

        // Place all phases of the project into the request
        request.setAttribute("phases", phases);

        Deliverable[] deliverables = ActionsHelper.getAllDeliverablesForActivePhases(
                request, ActionsHelper.createDeliverableManager(request));
        Deliverable[] myDeliverables = ActionsHelper.getMyDeliverables(deliverables, myResources);
        Deliverable[] outstandingDeliverables = ActionsHelper.getOutstandingDeliverables(deliverables);

        request.setAttribute("myDeliverables", myDeliverables);
        request.setAttribute("outstandingDeliverables", outstandingDeliverables);

        // Obtain an array of all active phases of the project
        Phase[] activePhases = ActionsHelper.getActivePhases(phases);
        long currentTime = (new Date()).getTime();

        // These two arrays will contain Deadline near / Late / Completed codes for deliverables
        int[] myDeliverableStatuses = getDeliverableStatusCodes(myDeliverables, activePhases, currentTime);
        int[] outstandingDeliverableStatuses =
            getDeliverableStatusCodes(outstandingDeliverables, activePhases, currentTime);

        Format format = new SimpleDateFormat("MM.dd.yyyy hh:mm a");
        String[] myDeliverableDates = new String[myDeliverables.length];
        String[] outstandingDeliverableDates = new String[outstandingDeliverables.length];

        for (int i = 0; i < myDeliverables.length; ++i) {
            Deliverable deliverable = myDeliverables[i];
            if (deliverable.isComplete()) {
                myDeliverableDates[i] = format.format(deliverable.getCompletionDate());
            } else {
                Phase phase = ActionsHelper.getPhaseForDeliverable(activePhases, deliverable);
                myDeliverableDates[i] = format.format(phase.calcEndDate());
            }
        }
        for (int i = 0; i < outstandingDeliverables.length; ++i) {
            Deliverable deliverable = outstandingDeliverables[i];
            if (deliverable.isComplete()) {
                outstandingDeliverableDates[i] = format.format(deliverable.getCompletionDate());
            } else {
                Phase phase = ActionsHelper.getPhaseForDeliverable(activePhases, deliverable);
                outstandingDeliverableDates[i] = format.format(phase.calcEndDate());
            }
        }

        String[] myDeliverableLinks = generateDeliverableLinks(request, myDeliverables, phases);
        String[] outstandingDeliverableUserIds =
            getDeliverableUserIds(ActionsHelper.createResourceManager(request), outstandingDeliverables);
        String[] outstandingDeliverableSubmissionUserIds =
            getDeliverableSubmissionUserIds(request, outstandingDeliverables);

        request.setAttribute("myDeliverableDates", myDeliverableDates);
        request.setAttribute("outstandingDeliverableDates", outstandingDeliverableDates);
        request.setAttribute("myDeliverableStatuses", myDeliverableStatuses);
        request.setAttribute("outstandingDeliverableStatuses", outstandingDeliverableStatuses);
        request.setAttribute("myDeliverableLinks", myDeliverableLinks);
        request.setAttribute("outstandingDeliverableUserIds", outstandingDeliverableUserIds);
        request.setAttribute("outstandingDeliverableSubmissionUserIds", outstandingDeliverableSubmissionUserIds);

        String[] displayedStart = new String[phases.length];
        String[] displayedEnd = new String[phases.length];
        String[] originalStart = new String[phases.length];
        String[] originalEnd = new String[phases.length];
        String strOrigStartTime = messages.getMessage("viewProjectDetails.Timeline.OriginalStartTime") + " ";
        String strOrigEndTime = messages.getMessage("viewProjectDetails.Timeline.OriginalEndTime") + " ";
        long projectStartTime = phProj.getStartDate().getTime();
        // The following two arrays are used to display Gantt chart
        long[] ganttOffsets = new long[phases.length];
        long[] ganttLengths = new long[phases.length];
        // List of scorecard templates used for this project
        List scorecardTemplates = new ArrayList();

        // Iterate over all phases determining dates, durations and assigned scorecards
        for (int i = 0; i < phases.length; ++i) {
            // Get a phase for this iteration
            Phase phase = phases[i];

            Date startDate = phase.calcStartDate();
            Date endDate = phase.calcEndDate();

            // Determine the strings to display for start/end dates
            displayedStart[i] = format.format(startDate);
            originalStart[i] = strOrigStartTime + format.format(phase.getScheduledStartDate());
            displayedEnd[i] = format.format(endDate);
            originalEnd[i] = strOrigEndTime + format.format(phase.getScheduledEndDate());

            // Determine offsets and lengths of the bars in Gantt chart, in hours
            ganttOffsets[i] = (startDate.getTime() - projectStartTime) / (60 * 60 * 1000);
            ganttLengths[i] = (endDate.getTime() - startDate.getTime()) / (60 * 60 * 1000);

            // Get a scorecard template associated with this phase if any
            Scorecard scorecardTemplate = ActionsHelper.getScorecardTemplateForPhase(
                    ActionsHelper.createScorecardManager(request), phase);
            // If there is a scorecard template for the phase, store it in the list
            if (scorecardTemplate != null) {
                scorecardTemplates.add(scorecardTemplate);
            }
        }

        // This array will contain Open / Closing / Late / Closed codes for phases
        int[] phaseStatuseCodes = getPhaseStatusCodes(phases, currentTime);

        /*
         * Place all gathered information about phases into the request as attributes
         */

        // Place phases' start/end dates
        request.setAttribute("displayedStart", displayedStart);
        request.setAttribute("displayedEnd", displayedEnd);
        request.setAttribute("originalStart", originalStart);
        request.setAttribute("originalEnd", originalEnd);
        request.setAttribute("phaseStatuseCodes", phaseStatuseCodes);
        // Place phases durations for Gantt chart
        request.setAttribute("ganttOffsets", ganttOffsets);
        request.setAttribute("ganttLengths", ganttLengths);
        // Determine the amount of pixels to display in Gantt chart for every hour
        request.setAttribute("pixelsPerHour", ConfigHelper.getPixelsPerHour());
        // Place information about used scorecard templates
        request.setAttribute("scorecardTemplates", scorecardTemplates);

        // Obtain an instance of Resource Manager
        ResourceManager resMgr = ActionsHelper.createResourceManager(request);
        Resource[] allProjectResources = null;
        ExternalUser[] allProjectExtUsers = null;

        // Determine if the user has permission to view a list of resources for the project
        if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_PROJECT_RESOURCES_PERM_NAME)) {
            // Get an array of resources for the project
            allProjectResources = ActionsHelper.getAllResourcesForProject(resMgr, project);

            // Prepare an array to store External User IDs
            long[] extUserIds = new long[allProjectResources.length];
            // Fill the array with user IDs retrieved from resource properties
            for (int i = 0; i < allProjectResources.length; ++i) {
                String userID = (String) allProjectResources[i].getProperty("External Reference ID");
                extUserIds[i] = Long.valueOf(userID).longValue();
            }

            ExternalUser[] extUsers = null;
            // If there are no resource for this project defined,
            // there will be no external users
            if (extUserIds.length != 0) {
                // Obtain an instance of User Retrieval
                UserRetrieval usrMgr = ActionsHelper.createUserRetrieval(request);
                // Retrieve external users for the list of resources using batch retrieval
                extUsers = usrMgr.retrieveUsers(extUserIds);
            } else {
                extUsers = new ExternalUser[0];
            }

            // This is final array for External User objects. It is needed because the previous
            // operation may return shorter array than there are resources for the project
            // (sometimes several resources can be associated with one external user)
            allProjectExtUsers = new ExternalUser[allProjectResources.length];

            for (int i = 0; i < extUserIds.length; ++i) {
                for (int j = 0; j < extUsers.length; ++j) {
                    if (extUsers[j].getId() == extUserIds[i]) {
                        allProjectExtUsers[i] = extUsers[j];
                        break;
                    }
                }
            }

            // Place resources and external users into the request
            request.setAttribute("resources", allProjectResources);
            request.setAttribute("users", allProjectExtUsers);
        }

        int[] phaseGroupIndexes = new int[phases.length];
        List phaseGroups = new ArrayList();
        int phaseGroupIdx = -1;
        PhaseGroup phaseGroup = null;
        Resource[] submitters = null;

        for (int i = 0; i < phases.length; ++i) {
            // Get a phase for the current iteration
            Phase phase = phases[i];
            String phaseName = phase.getPhaseType().getName();

            if (phaseGroupIdx == -1 ||
                    !ConfigHelper.isPhaseGroupContainsPhase(phaseGroupIdx, phaseName)) {
                phaseGroupIdx = ConfigHelper.findPhaseGroupForPhaseName(phaseName);

                String appFuncName = ConfigHelper.getPhaseGroupAppFunction(phaseGroupIdx);

                if (!appFuncName.equals(Constants.VIEW_REGISTRANTS_APP_FUNC) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_REGISTRATIONS_PERM_NAME)) {
                    phaseGroup = new PhaseGroup();
                    phaseGroups.add(phaseGroup);

                    phaseGroup.setName(messages.getMessage(ConfigHelper.getPhaseGroupNameKey(phaseGroupIdx)));
                    phaseGroup.setAppFunc(appFuncName);
                } else {
                    phaseGroup = null;
                }
            }

            if (phaseGroup == null) {
                phaseGroupIndexes[i] = -1;
                continue;
            }

            String phaseStatus = phase.getPhaseStatus().getName();

            if (phaseStatus.equalsIgnoreCase("Closed") || phaseStatus.equalsIgnoreCase("Open")) {
                phaseGroup.setPhaseOpen(true);
            }

            boolean isAfterAppealsResponse = ActionsHelper.isAfterAppealsResponse(phases, i);
            boolean canSeeSubmitters = (isAfterAppealsResponse ||
                    AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME));

            if (canSeeSubmitters) {
                if (submitters == null) {
                    if (allProjectResources == null) {
                        allProjectResources = ActionsHelper.getAllResourcesForProject(resMgr, project);
                    }
                    submitters = ActionsHelper.getAllSubmitters(allProjectResources);
                }
            } else {
                submitters = null;
            }

            phaseGroup.setSubmitters(submitters);

            phaseGroupIndexes[i] = phaseGroups.size() - 1;

            if (!phaseGroup.isPhaseOpen()) {
                continue;
            }

            if (phaseGroup.getAppFunc().equals(Constants.VIEW_REGISTRANTS_APP_FUNC)) {
                // TODO: Retrieve submitters' emails as well
            }

            if (phaseGroup.getAppFunc().equals(Constants.VIEW_SUBMISSIONS_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.SUBMISSION_PHASE_NAME)) {
                Submission[] submissions = null;

                if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }

                boolean mayViewMostRecentAfterAppealsResponse =
                    AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_AAR_PERM_NAME);

                if (submissions == null &&
                        ((mayViewMostRecentAfterAppealsResponse && isAfterAppealsResponse) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_PERM_NAME))) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }
                if (submissions == null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_SUBM_PERM_NAME)) {
                    // Obtain an instance of Upload Manager
                    UploadManager upMgr = ActionsHelper.createUploadManager(request);
                    SubmissionStatus[] allSubmissionStatuses = upMgr.getAllSubmissionStatuses();

                    // Get "my" (submitter's) resource
                    Resource myResource = ActionsHelper.getMyResourceForPhase(request, null);

                    Filter filterProject = SubmissionFilterBuilder.createProjectIdFilter(project.getId());
                    Filter filterStatus = SubmissionFilterBuilder.createSubmissionStatusIdFilter(
                            ActionsHelper.findSubmissionStatusByName(allSubmissionStatuses, "Active").getId());
                    Filter filterResource = SubmissionFilterBuilder.createResourceIdFilter(myResource.getId());

                    Filter filter =
                        new AndFilter(Arrays.asList(new Filter[] {filterProject, filterStatus, filterResource}));

                    submissions = upMgr.searchSubmissions(filter);
                }
                if (submissions == null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SCREENER_SUBM_PERM_NAME)) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }

                phaseGroup.setSubmissions(submissions);

                if (submissions != null && submissions.length != 0) {
                    long[] uploadIds = new long[submissions.length];

                    for (int j = 0; j < submissions.length; ++j) {
                        uploadIds[j] = submissions[j].getUpload().getId();
                    }

                    ScreeningManager scrMgr = ActionsHelper.createScreeningManager(request);
                    ScreeningTask[] tasks = scrMgr.getScreeningTasks(uploadIds, true);

                    phaseGroup.setScreeningTasks(tasks);
                }
            }

            if (phaseGroup.getAppFunc().equals(Constants.VIEW_SUBMISSIONS_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.SCREENING_PHASE_NAME) &&
                    phaseGroup.getSubmissions() != null) {
                Submission[] submissions = phaseGroup.getSubmissions();

                if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SCREENER_SUBM_PERM_NAME) &&
                        !AuthorizationHelper.hasUserRole(request, Constants.PRIMARY_SCREENER_ROLE_NAME)) {
                    Resource[] my = ActionsHelper.getMyResourcesForPhase(request, phase);
                    ScreeningTask[] allTasks = phaseGroup.getScreeningTasks();
                    List tempSubs = new ArrayList();
                    List tasks = new ArrayList();

                    for (int j = 0; j < submissions.length; ++j) {
                        for (int k = 0; k < my.length; ++k) {
                            if (my[k].getSubmission() != null &&
                                    my[k].getSubmission().longValue() == submissions[j].getId()) {
                                tempSubs.add(submissions[j]);
                                tasks.add(allTasks[j]);
                            }
                        }
                    }

                    submissions = (Submission[]) tempSubs.toArray(new Submission[tempSubs.size()]);
                    phaseGroup.setSubmissions(submissions);
                    phaseGroup.setScreeningTasks((ScreeningTask[]) tasks.toArray(new ScreeningTask[tasks.size()]));
                }

                Resource[] screeners = null;

                if (allProjectResources != null) {
                    screeners = ActionsHelper.getResourcesForPhase(allProjectResources, phase);
                } else {
                    screeners = ActionsHelper.getAllResourcesForPhase(
                            ActionsHelper.createResourceManager(request), phase);
                }

                phaseGroup.setReviewers(screeners);

                // No need to fetch auto screening results if there are no submissions
                if (submissions.length == 0) {
                    continue;
                }

                // Obtain an instance of Scorecard Manager
                ScorecardManager scrMgr = ActionsHelper.createScorecardManager(request);
                ScorecardType[] allScorecardTypes = scrMgr.getAllScorecardTypes();

                List submissionIds = new ArrayList();

                for (int j = 0; j < submissions.length; ++j) {
                    submissionIds.add(new Long(submissions[j].getId()));
                }

                Filter filterSubmissions = new InFilter("submission", submissionIds);
                Filter filterScorecard = new EqualToFilter("scorecardType",
                        new Long(ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Screening").getId()));

                Filter filter = new AndFilter(filterSubmissions, filterScorecard);

                // Obtain an instance of Review Manager
                ReviewManager revMgr = ActionsHelper.createReviewManager(request);
                Review[] reviews = revMgr.searchReviews(filter, false);

                phaseGroup.setScreenings(reviews);
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.VIEW_REVIEWS_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.REVIEW_PHASE_NAME)) {
                Submission[] submissions = null;

                if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }

                boolean mayViewMostRecentAfterAppealsResponse =
                    AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_AAR_PERM_NAME);

                if (submissions == null &&
                        ((mayViewMostRecentAfterAppealsResponse && isAfterAppealsResponse) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_PERM_NAME))) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }
                if (submissions == null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_SUBM_PERM_NAME)) {
                    // Obtain an instance of Upload Manager
                    UploadManager upMgr = ActionsHelper.createUploadManager(request);
                    SubmissionStatus[] allSubmissionStatuses = upMgr.getAllSubmissionStatuses();

                    // Get "my" (submitter's) resource
                    Resource myResource = ActionsHelper.getMyResourceForPhase(request, null);

                    Filter filterProject = SubmissionFilterBuilder.createProjectIdFilter(project.getId());
                    Filter filterStatus = SubmissionFilterBuilder.createSubmissionStatusIdFilter(
                            ActionsHelper.findSubmissionStatusByName(allSubmissionStatuses, "Active").getId());
                    Filter filterResource = SubmissionFilterBuilder.createResourceIdFilter(myResource.getId());

                    Filter filter =
                        new AndFilter(Arrays.asList(new Filter[] {filterProject, filterStatus, filterResource}));

                    submissions = upMgr.searchSubmissions(filter);
                }
                if (submissions == null) {
                    submissions = new Submission[0];
                }

                phaseGroup.setSubmissions(submissions);

                Resource[] reviewers = null;

                if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_REVIEWER_REVIEWS_PERM_NAME)) {
                    // Get "my" (reviewer's) resource
                    reviewers = ActionsHelper.getMyResourcesForPhase(request, phase);
                }

                if (reviewers == null) {
                    if (allProjectResources != null) {
                        reviewers = ActionsHelper.getResourcesForPhase(allProjectResources, phase);
                    } else {
                        reviewers = ActionsHelper.getAllResourcesForPhase(
                                ActionsHelper.createResourceManager(request), phase);
                    }
                }

                phaseGroup.setReviewers(reviewers);

                // Obtain an instance of Scorecard Manager
                ScorecardManager scrMgr = ActionsHelper.createScorecardManager(request);
                ScorecardType[] allScorecardTypes = scrMgr.getAllScorecardTypes();

                List submissionIds = new ArrayList();

                for (int j = 0; j < submissions.length; ++j) {
                    submissionIds.add(new Long(submissions[j].getId()));
                }

                List reviewerIds = new ArrayList();

                for (int j = 0; j < reviewers.length; ++j) {
                    reviewerIds.add(new Long(reviewers[j].getId()));
                }

                Filter filterSubmissions =
                    (submissionIds.isEmpty()) ? null : new InFilter("submission", submissionIds);
                Filter filterReviewers = new InFilter("reviewer", reviewerIds);
                Filter filterScorecard = new EqualToFilter("scorecardType",
                        new Long(ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review").getId()));

                List reviewFilters = new ArrayList();
                reviewFilters.add(filterReviewers);
                reviewFilters.add(filterScorecard);
                if (filterSubmissions != null) {
                    reviewFilters.add(filterSubmissions);
                }

                Filter filterForReviews = new AndFilter(reviewFilters);

                // Obtain an instance of Review Manager
                ReviewManager revMgr = ActionsHelper.createReviewManager(request);
                Review[] ungroupedReviews = revMgr.searchReviews(filterForReviews, false);

                // Obtain an instance of Upload Manager
                UploadManager upMgr = ActionsHelper.createUploadManager(request);
                UploadStatus[] allUploadStatuses = upMgr.getAllUploadStatuses();
                UploadType[] allUploadTypes = upMgr.getAllUploadTypes();

                Filter filterResource = new InFilter("resource_id", reviewerIds);
                Filter filterStatus = UploadFilterBuilder.createUploadStatusIdFilter(
                        ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active").getId());
                Filter filterType = UploadFilterBuilder.createUploadTypeIdFilter(
                        ActionsHelper.findUploadTypeByName(allUploadTypes, "Test Case").getId());

                Filter filterForUploads =
                    new AndFilter(Arrays.asList(new Filter[] {filterResource, filterStatus, filterType}));

                Upload[] testCases = upMgr.searchUploads(filterForUploads);

                phaseGroup.setTestCases(testCases);

                Review[][] reviews = new Review[submissions.length][];
                Date[] reviewDates = new Date[submissions.length];

                for (int j = 0; j < submissions.length; ++j) {
                    Date latestDate = null;
                    Review[] innerReviews = new Review[reviewers.length];
                    Arrays.fill(innerReviews, null);

                    for (int k = 0; k < reviewers.length; ++k) {
                        for (int l = 0; l < ungroupedReviews.length; ++l) {
                            Review ungrouped = ungroupedReviews[l];
                            if (ungrouped.getAuthor() == reviewers[k].getId() &&
                                    ungrouped.getSubmission() == submissions[j].getId()) {
                                innerReviews[k] = ungrouped;
                                if (!ungrouped.isCommitted()) {
                                    continue;
                                }
                                if (latestDate == null || latestDate.before(ungrouped.getModificationTimestamp())) {
                                    latestDate = ungrouped.getModificationTimestamp();
                                }
                            }
                        }
                    }

                    reviews[j] = innerReviews;
                    reviewDates[j] = latestDate;
                }
                phaseGroup.setReviews(reviews);
                phaseGroup.setReviewDates(reviewDates);
            }

            if ((phaseGroup.getAppFunc().equalsIgnoreCase(Constants.AGGREGATION_APP_FUNC) ||
                    phaseGroup.getAppFunc().equalsIgnoreCase(Constants.FINAL_FIX_APP_FUNC) ||
                    phaseGroup.getAppFunc().equalsIgnoreCase(Constants.APPROVAL_APP_FUNC)) &&
                    phaseGroup.getSubmitters() != null && phaseGroup.getSubmissions() == null) {
                Submission[] submissions = null;

                boolean mayViewMostRecentAfterAppealsResponse =
                    AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_AAR_PERM_NAME);

                if ((mayViewMostRecentAfterAppealsResponse && isAfterAppealsResponse) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_PERM_NAME) ||
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_WINNING_SUBM_PERM_NAME)) {
                    submissions =
                        ActionsHelper.getMostRecentSubmissions(ActionsHelper.createUploadManager(request), project);
                }
                if (submissions == null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_SUBM_PERM_NAME)) {
                    // Obtain an instance of Upload Manager
                    UploadManager upMgr = ActionsHelper.createUploadManager(request);
                    SubmissionStatus[] allSubmissionStatuses = upMgr.getAllSubmissionStatuses();

                    // Get "my" (submitter's) resource
                    Resource myResource = ActionsHelper.getMyResourceForPhase(request, null);

                    Filter filterProject = SubmissionFilterBuilder.createProjectIdFilter(project.getId());
                    Filter filterStatus = SubmissionFilterBuilder.createSubmissionStatusIdFilter(
                            ActionsHelper.findSubmissionStatusByName(allSubmissionStatuses, "Active").getId());
                    Filter filterResource = SubmissionFilterBuilder.createResourceIdFilter(myResource.getId());

                    Filter filter =
                        new AndFilter(Arrays.asList(new Filter[] {filterProject, filterStatus, filterResource}));

                    submissions = upMgr.searchSubmissions(filter);
                }
                if (submissions != null) {
                    phaseGroup.setSubmissions(submissions);
                }
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.AGGREGATION_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.AGGREGATION_PHASE_NAME) &&
                    phaseGroup.getSubmitters() != null) {
                Resource winner = ActionsHelper.getWinner(phaseGroup.getSubmitters());
                phaseGroup.setWinner(winner);

                Resource[] aggregator = null;

                if (allProjectResources != null) {
                    aggregator = ActionsHelper.getResourcesForPhase(allProjectResources, phase);
                } else {
                    aggregator = ActionsHelper.getAllResourcesForPhase(resMgr, phase);
                }
                if (aggregator == null || aggregator.length == 0) {
                    continue;
                }

                Filter filterResource = new EqualToFilter("reviewer", new Long(aggregator[0].getId()));
                Filter filterProject = new EqualToFilter("project", new Long(project.getId()));

                Filter filter = new AndFilter(filterResource, filterProject);

                // Obtain an instance of Review Manager
                ReviewManager revMgr = ActionsHelper.createReviewManager(request);
                Review[] reviews = revMgr.searchReviews(filter, true);

                if (reviews.length != 0) {
                    phaseGroup.setAggregation(reviews[0]);
                }
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.AGGREGATION_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.AGGREGATION_REVIEW_PHASE_NAME) &&
                    phaseGroup.getAggregation() != null) {
                Review aggregation = phaseGroup.getAggregation();

                if (aggregation.isCommitted()) {
                    int j = 0;
                    for (; j < aggregation.getNumberOfComments(); ++j) {
                        // Get a comment for the current iteration
                        Comment comment = aggregation.getComment(j);
                        String commentType = comment.getCommentType().getName();

                        if (commentType.equalsIgnoreCase("Aggregation Review Comment") ||
                                commentType.equalsIgnoreCase("Submitter Comment")) {
                            String extraInfo = (String) comment.getExtraInfo();
                            if (!("Approved".equalsIgnoreCase(extraInfo) ||
                                    "Rejected".equalsIgnoreCase(extraInfo))) {
                                break;
                            }
                        }
                    }
                    if (j == aggregation.getNumberOfComments()) {
                        phaseGroup.setAggregationReviewCommitted(true);
                    }
                }
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.FINAL_FIX_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.FINAL_FIX_PHASE_NAME) &&
                    phaseGroup.getSubmitters() != null) {
                Resource winner = phaseGroup.getWinner();
                if (winner == null) {
                    winner = ActionsHelper.getWinner(phaseGroup.getSubmitters());
                    phaseGroup.setWinner(winner);
                }
                if (winner == null) {
                    continue;
                }

                // Obtain an instance of Upload Manager
                UploadManager upMgr = ActionsHelper.createUploadManager(request);
                UploadStatus[] allUploadStatuses = upMgr.getAllUploadStatuses();
                UploadType[] allUploadTypes = upMgr.getAllUploadTypes();

                Filter filterStatus = UploadFilterBuilder.createUploadStatusIdFilter(
                        ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active").getId());
                Filter filterType = UploadFilterBuilder.createUploadTypeIdFilter(
                        ActionsHelper.findUploadTypeByName(allUploadTypes, "Final Fix").getId());
                Filter filterResource = UploadFilterBuilder.createResourceIdFilter(winner.getId());

                Filter filter = new AndFilter(Arrays.asList(
                        new Filter[] {filterStatus, filterType, filterResource}));
                Upload[] uploads = upMgr.searchUploads(filter);
                if (uploads.length != 0) {
                    phaseGroup.setFinalFix(uploads[0]);
                }
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.FINAL_FIX_APP_FUNC) &&
                    phaseName.equalsIgnoreCase(Constants.FINAL_REVIEW_PHASE_NAME) &&
                    phaseGroup.getSubmitters() != null) {
                Resource winner = phaseGroup.getWinner();
                if (winner == null) {
                    winner = ActionsHelper.getWinner(phaseGroup.getSubmitters());
                    phaseGroup.setWinner(winner);
                }
                if (winner == null) {
                    continue;
                }

                Resource[] reviewer = null;

                if (allProjectResources != null) {
                    reviewer = ActionsHelper.getResourcesForPhase(allProjectResources, phase);
                } else {
                    reviewer = ActionsHelper.getAllResourcesForPhase(resMgr, phase);
                }
                if (reviewer == null || reviewer.length == 0) {
                    continue;
                }

                Filter filterResource = new EqualToFilter("reviewer", new Long(reviewer[0].getId()));
                Filter filterProject = new EqualToFilter("project", new Long(project.getId()));

                Filter filter = new AndFilter(filterResource, filterProject);

                // Obtain an instance of Review Manager
                ReviewManager revMgr = ActionsHelper.createReviewManager(request);
                Review[] reviews = revMgr.searchReviews(filter, true);

                if (reviews.length != 0) {
                    phaseGroup.setFinalReview(reviews[0]);
                }
            }

            if (phaseGroup.getAppFunc().equalsIgnoreCase(Constants.APPROVAL_APP_FUNC) &&
                    phaseGroup.getSubmitters() != null) {
                Resource winner = ActionsHelper.getWinner(phaseGroup.getSubmitters());
                phaseGroup.setWinner(winner);

                Resource[] approver = null;

                if (allProjectResources != null) {
                    approver = ActionsHelper.getResourcesForPhase(allProjectResources, phase);
                } else {
                    approver = ActionsHelper.getAllResourcesForPhase(resMgr, phase);
                }
                if (approver == null || approver.length == 0) {
                    continue;
                }

                // Obtain an instance of Scorecard Manager
                ScorecardManager scrMgr = ActionsHelper.createScorecardManager(request);
                ScorecardType[] allScorecardTypes = scrMgr.getAllScorecardTypes();

                Filter filterResource = new EqualToFilter("reviewer", new Long(approver[0].getId()));
                Filter filterProject = new EqualToFilter("project", new Long(project.getId()));
                Filter filterScorecard = new EqualToFilter("scorecardType",
                        new Long(ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Client Review").getId()));

                Filter filter = new AndFilter(Arrays.asList(
                        new Filter[] {filterResource, filterProject, filterScorecard}));

                // Obtain an instance of Review Manager
                ReviewManager revMgr = ActionsHelper.createReviewManager(request);
                Review[] reviews = revMgr.searchReviews(filter, true);

                if (reviews.length != 0) {
                    phaseGroup.setApproval(reviews[0]);
                }
            }
        }

        request.setAttribute("phaseGroupIndexes", phaseGroupIndexes);
        request.setAttribute("phaseGroups", phaseGroups);

        boolean sendTLNotifications = false;

        if (AuthorizationHelper.isUserLoggedIn(request)) {
            Filter filterTNproject = NotificationFilterBuilder.createProjectIdFilter(project.getId());
            Filter filterTNuser = NotificationFilterBuilder.createExternalRefIdFilter(
                    AuthorizationHelper.getLoggedInUserId(request));
            Filter filterTNtype = NotificationFilterBuilder.createNotificationTypeIdFilter(1);
            Filter filterTN = new AndFilter(filterTNproject, new AndFilter(filterTNuser, filterTNtype));

            Notification[] notifications = resMgr.searchNotifications(filterTN);
            sendTLNotifications = (notifications.length != 0);
        }

        request.setAttribute("sendTLNotifications", (sendTLNotifications) ? "On" : "Off");

        request.setAttribute("passingMinimum", new Float(75.0));

        // Check permissions
        request.setAttribute("isManager",
                new Boolean(AuthorizationHelper.hasUserRole(request, Constants.MANAGER_ROLE_NAME)));
        request.setAttribute("isAllowedToEditProjects",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.EDIT_PROJECT_DETAILS_PERM_NAME)));
        request.setAttribute("isAllowedToContactPM",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.CONTACT_PM_PERM_NAME)));
        request.setAttribute("isAllowedToSetTL",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.SET_TL_NOTIFY_PERM_NAME)));
        request.setAttribute("isAllowedToViewSVNLink",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SVN_LINK_PERM_NAME)));
        request.setAttribute("isAllowedToViewPayment",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_PAY_INFO_PERM_NAME)));
        request.setAttribute("isAllowedToViewAllPayment",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_PAYMENT_INFO_PERM_NAME)));
        request.setAttribute("isAllowedToViewResources",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_PROJECT_RESOURCES_PERM_NAME)));
        request.setAttribute("isAllowedToPerformScreening",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_SCREENING_PERM_NAME) &&
                        ActionsHelper.getPhase(phases, true, Constants.SCREENING_PHASE_NAME) != null));
        request.setAttribute("isAllowedToViewScreening",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SCREENING_PERM_NAME)));
        request.setAttribute("isAllowedToEditHisReviews",
                new Boolean(AuthorizationHelper.hasUserPermission(
                        request, Constants.VIEW_REVIEWER_REVIEWS_PERM_NAME) &&
                        (ActionsHelper.getPhase(phases, true, Constants.REVIEW_PHASE_NAME) != null ||
                                ActionsHelper.getPhase(phases, true, Constants.APPEALS_PHASE_NAME) != null ||
                                ActionsHelper.getPhase(phases, true, Constants.APPEALS_RESPONSE_PHASE_NAME) != null)));
        request.setAttribute("isAllowedToUploadTC",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.UPLOAD_TEST_CASES_PERM_NAME)));
        request.setAttribute("isAllowedToPerformAggregation",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_AGGREGATION_PERM_NAME)));
        request.setAttribute("isAllowedToPerformAggregationReview",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_AGGREG_REVIEW_PERM_NAME)));
        request.setAttribute("isAllowedToUploadFF",
                new Boolean(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_FINAL_FIX_PERM_NAME)));
        request.setAttribute("isAllowedToPerformFinalReview",
                new Boolean(ActionsHelper.getPhase(phases, true, Constants.FINAL_REVIEW_PHASE_NAME) != null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_FINAL_REVIEW_PERM_NAME)));
        request.setAttribute("isAllowedToPerformApproval",
                new Boolean(ActionsHelper.getPhase(phases, true, Constants.APPROVAL_PHASE_NAME) != null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_APPROVAL_PERM_NAME)));

        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * This method is an implementation of &quot;Contact Manager&quot; Struts Action defined for
     * this assembly, which is supposed to send a message entered by user to the manager of some
     * project. This action gets executed twice &#x96; once to display the page with the form, and
     * once to process the message entered by user on that form.
     *
     * @return an action forward to the appropriate page. If no error has occured and this action
     *         was called the first time, the forward will be to contactManager.jsp page, which
     *         displays the form where user can enter his message. If this action was called during
     *         the post back (the second time), then the request should contain the message to send
     *         entered by user. In this case, this method verifies if everything is correct, sends
     *         the message to manager and returns a forward to the View Project Details page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     * @throws ConfigManagerException
     *             if any error occurs while loading the document generator's configuration.
     */
    public ActionForward contactManager(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException, ConfigManagerException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification = checkForCorrectProjectId(mapping, request, Constants.CONTACT_PM_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Determine if this request is a post back
        boolean postBack = (request.getParameter("postBack") != null);

        if (!postBack) {
            // Retrieve some basic project info (such as icons' names) and place it into request
            ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), getResources(request));
            return mapping.findForward(Constants.DISPLAY_PAGE_FORWARD_NAME);
        }

        DocumentGenerator docGenerator = DocumentGenerator.getInstance();
        Template docTemplate = docGenerator.getTemplate(
                ConfigHelper.getContactManagerEmailSrcType(), ConfigHelper.getContactManagerEmailTemplate());

        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * This method is an implementation of &quot;Upload Submission&quot; Struts Action defined for
     * this assembly, which is supposed to let the user upload his submission to the server. This
     * action gets executed twice &#x96; once to display the page with the form, and once to process
     * the uploaded file.
     *
     * @return an action forward to the appropriate page. If no error has occured and this action
     *         was called the first time, the forward will be to uploadSubmission.jsp page, which
     *         displays the form where user can specify the file he/she wants to upload. If this
     *         action was called during the post back (the second time), then the request should
     *         contain the file uploaded by user. In this case, this method verifies if everything
     *         is correct, stores the file on file server and returns a forward to the View Project
     *         Details page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward uploadSubmission(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectProjectId(mapping, request, Constants.PERFORM_SUBM_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Retrieve current project
        Project project = verification.getProject();
        // Get all phases for the current project
        Phase[] phases = ActionsHelper.getPhasesForProject(ActionsHelper.createPhaseManager(request), project);

        if (ActionsHelper.getPhase(phases, true, Constants.SUBMISSION_PHASE_NAME) == null) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request), request,
                    Constants.PERFORM_SUBM_PERM_NAME, "Error.IncorrectPhase");
        }

        // Determine if this request is a post back
        boolean postBack = (request.getParameter("postBack") != null);

        if (postBack != true) {
            // Retrieve some basic project info (such as icons' names) and place it into request
            ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), getResources(request));
            return mapping.findForward(Constants.DISPLAY_PAGE_FORWARD_NAME);
        }

        DynaValidatorForm uploadSubmissionForm = (DynaValidatorForm) form;
        FormFile file = (FormFile) uploadSubmissionForm.get("file");

        StrutsRequestParser parser = new StrutsRequestParser();
        parser.AddFile(file);

        // Obtain an instance of File Upload Manager
        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);

        FileUploadResult uploadResult = fileUpload.uploadFiles(request, parser);
        UploadedFile uploadedFile = uploadResult.getUploadedFile("file");

        // Get my resource
        Resource resource = ActionsHelper.getMyResourceForPhase(request, null);

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        SubmissionStatus[] submissionStatuses = upMgr.getAllSubmissionStatuses();

        Filter filterProject = SubmissionFilterBuilder.createProjectIdFilter(project.getId());
        Filter filterResource = SubmissionFilterBuilder.createResourceIdFilter(resource.getId());
        Filter filterStatus = SubmissionFilterBuilder.createSubmissionStatusIdFilter(
                ActionsHelper.findSubmissionStatusByName(submissionStatuses, "Active").getId());

        Filter filter = new AndFilter(Arrays.asList(new Filter[] {filterProject, filterResource, filterStatus}));

        Submission[] submissions = upMgr.searchSubmissions(filter);
        Submission submission = (submissions.length != 0) ? submissions[0] : null;
        Upload upload = (submission != null) ? submission.getUpload() : null;
        Upload deletedUpload = null;

        UploadStatus[] uploadStatuses = upMgr.getAllUploadStatuses();

        if (upload == null) {
            upload = new Upload();

            UploadType[] uploadTypes = upMgr.getAllUploadTypes();

            upload.setProject(project.getId());
            upload.setOwner(resource.getId());
            upload.setUploadStatus(ActionsHelper.findUploadStatusByName(uploadStatuses, "Active"));
            upload.setUploadType(ActionsHelper.findUploadTypeByName(uploadTypes, "Submission"));
            upload.setParameter(uploadedFile.getFileId());

            submission = new Submission();
            submission.setUpload(upload);
            submission.setSubmissionStatus(ActionsHelper.findSubmissionStatusByName(submissionStatuses, "Active"));
        } else {
            deletedUpload = upload;

            upload = new Upload();
            upload.setProject(deletedUpload.getProject());
            upload.setOwner(deletedUpload.getOwner());
            upload.setUploadStatus(deletedUpload.getUploadStatus());
            upload.setUploadType(deletedUpload.getUploadType());
            upload.setParameter(uploadedFile.getFileId());

            submission.setUpload(upload);

            deletedUpload.setUploadStatus(ActionsHelper.findUploadStatusByName(uploadStatuses, "Deleted"));
        }

        // Obtain an instance of Screening Manager
        ScreeningManager scrMgr = ActionsHelper.createScreeningManager(request);
        // Get the name (id) of the user performing the operations
        String operator = Long.toString(AuthorizationHelper.getLoggedInUserId(request));

        if (deletedUpload != null) {
            upMgr.updateUpload(deletedUpload, operator);
        }
        upMgr.createUpload(upload, operator);

        if (submissions.length == 0) {
            upMgr.createSubmission(submission, operator);
        } else {
            upMgr.updateSubmission(submission, operator);
        }

        scrMgr.initiateScreening(upload.getId(), operator);

        return ActionsHelper.cloneForwardAndAppendToPath(
                mapping.findForward(Constants.SUCCESS_FORWARD_NAME), "&pid=" + project.getId());
    }

    /**
     * This method is an implementation of &quot;Download Submission&quot; Struts Action defined for
     * this assembly, which is supposed to let the user download a submission from the server.
     *
     * @return a <code>null</code> code if everything went fine, or an action forward to
     *         /jsp/userError.jsp page which will display the information about the cause of error.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     * @throws IOException
     *             if some error occurs during disk input/output operation.
     */
    public ActionForward downloadSubmission(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException, IOException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, "ViewSubmission");
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Get an upload the user wants to download
        Upload upload = verification.getUpload();

        // Verify that upload is a submission
        if (!upload.getUploadType().getName().equalsIgnoreCase("Submission")) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewSubmission", "Error.NotASubmission");
        }

        // Verify the status of upload and check whether the user has permission to download old uploads
        if (upload.getUploadStatus().getName().equalsIgnoreCase("Deleted") &&
                !AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewSubmission", "Error.UploadDeleted");
        }

        boolean noRights = true;

        if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
            noRights = false;
        }

        if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_SUBM_PERM_NAME)) {
            long owningResourceId = upload.getOwner();
            Resource[] myResources = (Resource[]) request.getAttribute("myResources");
            for (int i = 0; i < myResources.length; ++i) {
                if (myResources[i].getId() == owningResourceId) {
                    noRights = false;
                    break;
                }
            }
        }

        if (noRights && AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_PERM_NAME)) {
            noRights = false;
        }

        if (noRights && AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SCREENER_SUBM_PERM_NAME)) {
            noRights = false; // TODO: Check if screener can download this submission
        }

        if (noRights && AuthorizationHelper.hasUserPermission(request, Constants.VIEW_WINNING_SUBM_PERM_NAME)) {
            // Obtain an instance of Resource Manager
            ResourceManager resMgr = ActionsHelper.createResourceManager(request);
            Resource submitter = resMgr.getResource(upload.getOwner());

            if ("1".equals(submitter.getProperty("Placement"))) {
                noRights = false;
            }
        }

        if (noRights && AuthorizationHelper.hasUserPermission(request, Constants.VIEW_RECENT_SUBM_AAR_PERM_NAME)) {
            // Obtain an instance of Resource Manager
            ResourceManager resMgr = ActionsHelper.createResourceManager(request);
            Resource submitter = resMgr.getResource(upload.getOwner());
            String placement = (String) submitter.getProperty("Placement");

            if (placement != null && placement.trim().length() != 0) {
                noRights = false;
            }
        }

        if (noRights) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewSubmission", "Error.NoPermission");
        }

        Filter filterProject = SubmissionFilterBuilder.createProjectIdFilter(upload.getProject());
        Filter filterResource = SubmissionFilterBuilder.createResourceIdFilter(upload.getOwner());
        Filter filterUpload = SubmissionFilterBuilder.createUploadIdFilter(upload.getId());

        Filter filter = new AndFilter(Arrays.asList(new Filter[] {filterProject, filterResource, filterUpload}));

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        Submission[] submissions = upMgr.searchSubmissions(filter);
        Submission submission = (submissions.length != 0) ? submissions[0] : null;

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);
        UploadedFile uploadedFile = fileUpload.getUploadedFile(upload.getParameter());

        InputStream in = uploadedFile.getInputStream();

        response.setHeader("Content-Type", "application/octet-stream");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setIntHeader("Content-Length", (int) uploadedFile.getSize());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"submission-" + submission.getId() +
                "-" + uploadedFile.getRemoteFileName() + "\"");

        response.flushBuffer();

        OutputStream out = null;

        try {
            out = response.getOutputStream();
            byte[] buffer = new byte[65536];

            for (;;) {
                int numOfBytesRead = in.read(buffer);
                if (numOfBytesRead == -1) {
                    break;
                }
                out.write(buffer, 0, numOfBytesRead);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }

        return null;
    }

    /**
     * This method is an implementation of &quot;Upload Final Fix&quot; Struts Action defined for
     * this assembly, which is supposed to let the user upload his submission to the server. This
     * action gets executed twice &#x96; once to display the page with the form, and once to process
     * the uploaded file.
     *
     * @return an action forward to the appropriate page. If no error has occured and this action
     *         was called the first time, the forward will be to uploadFinalFix.jsp page, which
     *         displays the form where user can specify the file he/she wants to upload. If this
     *         action was called during the post back (the second time), then the request should
     *         contain the file uploaded by user. In this case, this method verifies if everything
     *         is correct, stores the file on file server and returns a forward to the View Project
     *         Details page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward uploadFinalFix(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectProjectId(mapping, request, Constants.PERFORM_FINAL_FIX_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Determine if this request is a post back
        boolean postBack = (request.getParameter("postBack") != null);

        if (postBack != true) {
            // Retrieve some basic project info (such as icons' names) and place it into request
            ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), getResources(request));
            return mapping.findForward(Constants.DISPLAY_PAGE_FORWARD_NAME);
        }

        // Retrieve current project
        Project project = verification.getProject();

        // Get all phases for the current project
        Phase[] phases = ActionsHelper.getPhasesForProject(ActionsHelper.createPhaseManager(request), project);
        // Retrieve the current phase for the project
        Phase currentPhase = ActionsHelper.getPhase(phases, true, Constants.FINAL_FIX_PHASE_NAME);
        // Check that active phase is Final Fix
        if (currentPhase == null) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.PERFORM_FINAL_FIX_PERM_NAME, "Error.IncorrectPhase");
        }

        DynaValidatorForm uploadSubmissionForm = (DynaValidatorForm) form;
        FormFile file = (FormFile) uploadSubmissionForm.get("file");

        StrutsRequestParser parser = new StrutsRequestParser();
        parser.AddFile(file);

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);

        FileUploadResult uploadResult = fileUpload.uploadFiles(request, parser);
        UploadedFile uploadedFile = uploadResult.getUploadedFile("file");

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        UploadStatus[] allUploadStatuses = upMgr.getAllUploadStatuses();
        UploadType[] allUploadTypes = upMgr.getAllUploadTypes();

        // Get my resource
        Resource resource = ActionsHelper.getMyResourceForPhase(request, null);

        Filter filterProject = UploadFilterBuilder.createProjectIdFilter(project.getId());
        Filter filterResource = UploadFilterBuilder.createResourceIdFilter(resource.getId());
        Filter filterStatus = UploadFilterBuilder.createUploadStatusIdFilter(
                ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active").getId());
        Filter filterType = UploadFilterBuilder.createUploadTypeIdFilter(
                ActionsHelper.findUploadTypeByName(allUploadTypes, "Final Fix").getId());

        Filter filter = new AndFilter(
                Arrays.asList(new Filter[] {filterProject, filterResource, filterStatus, filterType}));

        Upload[] uploads = upMgr.searchUploads(filter);
        Upload oldUpload = (uploads.length != 0) ? uploads[0] : null;

        Upload upload = new Upload();

        upload.setProject(project.getId());
        upload.setOwner(resource.getId());
        upload.setUploadStatus(ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active"));
        upload.setUploadType(ActionsHelper.findUploadTypeByName(allUploadTypes, "Final Fix"));
        upload.setParameter(uploadedFile.getFileId());

        if (oldUpload != null) {
            oldUpload.setUploadStatus(ActionsHelper.findUploadStatusByName(allUploadStatuses, "Deleted"));
            upMgr.updateUpload(oldUpload, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
        }

        upMgr.createUpload(upload, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));

        return ActionsHelper.cloneForwardAndAppendToPath(
                mapping.findForward(Constants.SUCCESS_FORWARD_NAME), "&pid=" + project.getId());
    }

    /**
     * This method is an implementation of &quot;Download Final Fix&quot; Struts Action defined for
     * this assembly, which is supposed to let the user download a final fixes from the server.
     *
     * @return a <code>null</code> code if everything went fine, or an action forward to
     *         /jsp/userError.jsp page which will display the information about the cause of error.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     * @throws IOException
     *             if some error occurs during disk input/output operation.
     */
    public ActionForward downloadFinalFix(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException, IOException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, Constants.DOWNLOAD_FINAL_FIX_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Check that user has permissions to download Final Fixes
        if (!AuthorizationHelper.hasUserPermission(request, Constants.DOWNLOAD_FINAL_FIX_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_FINAL_FIX_PERM_NAME, "Error.NoPermission");
        }

        // Get an upload the user wants to download
        Upload upload = verification.getUpload();

        // Verify that upload is a Final Fix
        if (!upload.getUploadType().getName().equalsIgnoreCase("Final Fix")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_FINAL_FIX_PERM_NAME, "Error.NotAFinalFix");
        }
        // Verify the status of upload
        if (upload.getUploadStatus().getName().equalsIgnoreCase("Deleted")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_FINAL_FIX_PERM_NAME, "Error.UploadDeleted");
        }

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);
        UploadedFile uploadedFile = fileUpload.getUploadedFile(upload.getParameter());

        InputStream in = uploadedFile.getInputStream();

        response.setHeader("Content-Type", "application/octet-stream");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setIntHeader("Content-Length", (int) uploadedFile.getSize());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + uploadedFile.getRemoteFileName() + "\"");

        response.flushBuffer();

        OutputStream out = null;

        try {
            out = response.getOutputStream();
            byte[] buffer = new byte[65536];

            for (;;) {
                int numOfBytesRead = in.read(buffer);
                if (numOfBytesRead == -1) {
                    break;
                }
                out.write(buffer, 0, numOfBytesRead);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }

        return null;
    }

    /**
     * This method is an implementation of &quot;Upload Test Case&quot; Struts Action defined for
     * this assembly, which is supposed to let the user upload his test cases to the server. This
     * action gets executed twice &#x96; once to display the page with the form, and once to process
     * the uploaded file.
     *
     * @return an action forward to the appropriate page. If no error has occured and this action
     *         was called the first time, the forward will be to uploadTestCase.jsp page, which
     *         displays the form where user can specify the file he/she wants to upload. If this
     *         action was called during the post back (the second time), then the request should
     *         contain the file uploaded by user. In this case, this method verifies if everything
     *         is correct, stores the file on file server and returns a forward to the View Project
     *         Details page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward uploadTestCase(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectProjectId(mapping, request, Constants.UPLOAD_TEST_CASES_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Determine if this request is a post back
        boolean postBack = (request.getParameter("postBack") != null);

        if (postBack != true) {
            // Retrieve some basic project info (such as icons' names) and place it into request
            ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), getResources(request));
            return mapping.findForward(Constants.DISPLAY_PAGE_FORWARD_NAME);
        }

        // Retrieve current project
        Project project = verification.getProject();

        // Get all phases for the current project
        Phase[] phases = ActionsHelper.getPhasesForProject(ActionsHelper.createPhaseManager(request), project);
        // Retrieve the current phase for the project
        // TODO: Retrieve current phase correctly
        Phase currentPhase = ActionsHelper.getPhase(phases, true, Constants.REVIEW_PHASE_NAME);

        if (currentPhase == null) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.UPLOAD_TEST_CASES_PERM_NAME, "Error.IncorrectPhase");
        }

        DynaValidatorForm uploadSubmissionForm = (DynaValidatorForm) form;
        FormFile file = (FormFile) uploadSubmissionForm.get("file");

        StrutsRequestParser parser = new StrutsRequestParser();
        parser.AddFile(file);

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);

        FileUploadResult uploadResult = fileUpload.uploadFiles(request, parser);
        UploadedFile uploadedFile = uploadResult.getUploadedFile("file");

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        UploadStatus[] allUploadStatuses = upMgr.getAllUploadStatuses();
        UploadType[] allUploadTypes = upMgr.getAllUploadTypes();

        // Get my resource
        Resource resource = ActionsHelper.getMyResourceForPhase(
                request, ActionsHelper.getPhase(phases, false, Constants.REVIEW_PHASE_NAME));

        Filter filterProject = UploadFilterBuilder.createProjectIdFilter(project.getId());
        Filter filterResource = UploadFilterBuilder.createResourceIdFilter(resource.getId());
        Filter filterStatus = UploadFilterBuilder.createUploadStatusIdFilter(
                ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active").getId());
        Filter filterType = UploadFilterBuilder.createUploadTypeIdFilter(
                ActionsHelper.findUploadTypeByName(allUploadTypes, "Test Case").getId());

        Filter filter = new AndFilter(
                Arrays.asList(new Filter[] {filterProject, filterResource, filterStatus, filterType}));

        Upload[] uploads = upMgr.searchUploads(filter);
        Upload oldUpload = (uploads.length != 0) ? uploads[0] : null;

        Upload upload = new Upload();

        upload.setProject(project.getId());
        upload.setOwner(resource.getId());
        upload.setUploadStatus(ActionsHelper.findUploadStatusByName(allUploadStatuses, "Active"));
        upload.setUploadType(ActionsHelper.findUploadTypeByName(allUploadTypes, "Test Case"));
        upload.setParameter(uploadedFile.getFileId());

        if (oldUpload != null) {
            oldUpload.setUploadStatus(ActionsHelper.findUploadStatusByName(allUploadStatuses, "Deleted"));
            upMgr.updateUpload(oldUpload, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
        }

        upMgr.createUpload(upload, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));

        if (oldUpload != null) {
            // TODO: Send email notifications here
        }

        return ActionsHelper.cloneForwardAndAppendToPath(
                mapping.findForward(Constants.SUCCESS_FORWARD_NAME), "&pid=" + project.getId());
    }

    /**
     * This method is an implementation of &quot;Download Test Case&quot; Struts Action defined for
     * this assembly, which is supposed to let the user download a final fixes from the server.
     *
     * @return a <code>null</code> code if everything went fine, or an action forward to
     *         /jsp/userError.jsp page which will display the information about the cause of error.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     * @throws IOException
     *             if some error occurs during disk input/output operation.
     */
    public ActionForward downloadTestCase(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException, IOException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, Constants.DOWNLOAD_TEST_CASES_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Check that user has permissions to download Test Cases
        if (!AuthorizationHelper.hasUserPermission(request, Constants.DOWNLOAD_TEST_CASES_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_TEST_CASES_PERM_NAME, "Error.NoPermission");
        }

        // Get an upload the user wants to download
        Upload upload = verification.getUpload();

        // Verify that upload is Test Cases
        if (!upload.getUploadType().getName().equalsIgnoreCase("Test Case")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_TEST_CASES_PERM_NAME, "Error.NotTestCases");
        }
        // Verify the status of upload
        if (upload.getUploadStatus().getName().equalsIgnoreCase("Deleted")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_TEST_CASES_PERM_NAME, "Error.UploadDeleted");
        }

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);
        UploadedFile uploadedFile = fileUpload.getUploadedFile(upload.getParameter());

        InputStream in = uploadedFile.getInputStream();

        response.setHeader("Content-Type", "application/octet-stream");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setIntHeader("Content-Length", (int) uploadedFile.getSize());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + uploadedFile.getRemoteFileName() + "\"");

        response.flushBuffer();

        OutputStream out = null;

        try {
            out = response.getOutputStream();
            byte[] buffer = new byte[65536];

            for (;;) {
                int numOfBytesRead = in.read(buffer);
                if (numOfBytesRead == -1) {
                    break;
                }
                out.write(buffer, 0, numOfBytesRead);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }

        return null;
    }

    /**
     * This method is an implementation of &quot;Delete Submission&quot; Struts Action defined for
     * this assembly, which is supposed to delete (mark as deleted) submission for particular upload
     * (denoted by <code>uid</code> parameter). This action gets executed twice &#x96; once to
     * display the page with the confirmation, and once to process the confiremed delete request to
     * actually delete the submission.
     *
     * @return an action forward to the appropriate page. If no error has occured and this action
     *         was called the first time, the forward will be to /jsp/confirmDeleteSubmission.jsp
     *         page, which displays the confirmation dialog where user can confirm his intention to
     *         remove the submission. If this action was called during the post back (the second
     *         time), then this method verifies if everything is correct, and marks submission and
     *         its current active upload as deleted. After this it returns a forward to the View
     *         Project Details page.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward deleteSubmission(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, Constants.REMOVE_SUBM_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Retrieve the upload user tries to delete
        Upload upload = verification.getUpload();

        // Check that user has permissions to delete submission
        if (!AuthorizationHelper.hasUserPermission(request, Constants.REMOVE_SUBM_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.REMOVE_SUBM_PERM_NAME, "Error.NoPermission");
        }
        // Verify that the user is attempting to delete submission
        if (!upload.getUploadType().getName().equalsIgnoreCase("Submission")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.REMOVE_SUBM_PERM_NAME, "Error.NotASubmission2");
        }

        Filter filter = SubmissionFilterBuilder.createUploadIdFilter(upload.getId());
        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        Submission[] submissions = upMgr.searchSubmissions(filter);
        Submission submission = (submissions.length != 0) ? submissions[0] : null;

        if (submission == null || submission.getSubmissionStatus().getName().equalsIgnoreCase("Deleted")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.REMOVE_SUBM_PERM_NAME, "Error.SubmissionDeleted");
        }

        // Determine if this request is a post back
        boolean postBack = (request.getParameter("delete") != null);

        if (postBack != true) {
            // Retrieve some basic project info (such as icons' names) and place it into request
            ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), getResources(request));
            // Place upload ID into the request as attribute
            request.setAttribute("uid", new Long(upload.getId()));
            return mapping.findForward(Constants.DISPLAY_PAGE_FORWARD_NAME);
        }

        UploadStatus[] allUploadStatuses = upMgr.getAllUploadStatuses();
        SubmissionStatus[] allSubmissionStatuses = upMgr.getAllSubmissionStatuses();

        upload.setUploadStatus(ActionsHelper.findUploadStatusByName(allUploadStatuses, "Deleted"));
        submission.setSubmissionStatus(ActionsHelper.findSubmissionStatusByName(allSubmissionStatuses, "Deleted"));

        upMgr.updateUpload(upload, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
        upMgr.updateSubmission(submission, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));

        return ActionsHelper.cloneForwardAndAppendToPath(
                mapping.findForward(Constants.SUCCESS_FORWARD_NAME), "&pid=" + verification.getProject().getId());
    }

    /**
     * This method is an implementation of &quot;Download Document&quot; Struts Action defined for
     * this assembly, which is supposed to let the user download a review's document from the
     * server.
     *
     * @return a <code>null</code> code if everything went fine, or an action forward to
     *         /jsp/userError.jsp page which will display the information about the cause of error.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     * @throws IOException
     *             if some error occurs during disk input/output operation.
     */
    public ActionForward downloadDocument(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException, IOException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, Constants.DOWNLOAD_DOCUMENT_PERM_NAME);
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Check that user has permissions to download a Document
        if (!AuthorizationHelper.hasUserPermission(request, Constants.DOWNLOAD_DOCUMENT_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_DOCUMENT_PERM_NAME, "Error.NoPermission");
        }

        // Get an upload the user wants to download
        Upload upload = verification.getUpload();

        // Verify that upload is a Review Document
        if (!upload.getUploadType().getName().equalsIgnoreCase("Review Document")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_DOCUMENT_PERM_NAME, "Error.NotADocument");
        }
        // Verify the status of upload
        if (upload.getUploadStatus().getName().equalsIgnoreCase("Deleted")) {
            return ActionsHelper.produceErrorReport(mapping, getResources(request),
                    request, Constants.DOWNLOAD_DOCUMENT_PERM_NAME, "Error.UploadDeleted");
        }

        FileUpload fileUpload = ActionsHelper.createFileUploadManager(request);
        UploadedFile uploadedFile = fileUpload.getUploadedFile(upload.getParameter());

        InputStream in = uploadedFile.getInputStream();

        response.setHeader("Content-Type", "application/octet-stream");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setIntHeader("Content-Length", (int) uploadedFile.getSize());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + uploadedFile.getRemoteFileName() + "\"");

        response.flushBuffer();

        OutputStream out = null;

        try {
            out = response.getOutputStream();
            byte[] buffer = new byte[65536];

            for (;;) {
                int numOfBytesRead = in.read(buffer);
                if (numOfBytesRead == -1) {
                    break;
                }
                out.write(buffer, 0, numOfBytesRead);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }

        return null;
    }

    /**
     * This method is an implementation of &quot;View Auto Screening&quot; Struts Action defined for
     * this assembly, which is supposed to show the results of auto screening to user.
     *
     * @return &quot;success&quot; forward, which forwards to the /jsp/viewAutoScreening.jsp page
     *         (as defined in struts-config.xml file), or &quot;userError&quot; forward, which
     *         forwards to the /jsp/userError.jsp page, which displays information about an error
     *         that is usually caused by incorrect user input (such as absent upload id, or the lack
     *         of permissions, etc.).
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward viewAutoScreening(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws BaseException {
        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification =
            checkForCorrectUploadId(mapping, request, "ViewAutoScreening");
        // If any error has occured, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // TODO: Refactor permission check

        // Get an upload to display autoscreening results of
        Upload upload = verification.getUpload();

        // TODO: Should probably depict that we have AutoScreening not ViewSubmission
        // Verify that upload is a submission
        if (!upload.getUploadType().getName().equalsIgnoreCase("Submission")) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewAutoScreening", "Error.NotASubmission");
        }

        // Verify the status of upload and check whether the user has permission to download old uploads
        if (upload.getUploadStatus().getName().equalsIgnoreCase("Deleted") &&
                !AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewAutoScreening", "Error.UploadDeleted");
        }

        boolean noRights = true;

        if (AuthorizationHelper.hasUserPermission(request, Constants.VIEW_ALL_SUBM_PERM_NAME)) {
            noRights = false;
        }

        // Obtain an array of "my" resources
        Resource[] myResources = (Resource[]) request.getAttribute("myResources");

        if (noRights && AuthorizationHelper.hasUserPermission(request, Constants.VIEW_MY_SUBM_PERM_NAME)) {
            long owningResourceId = upload.getOwner();
            for (int i = 0; i < myResources.length; ++i) {
                if (myResources[i].getId() == owningResourceId) {
                    noRights = false;
                    break;
                }
            }
        }

        if (noRights) {
            return ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, "ViewAutoScreening", "Error.NoPermission");
        }

        // Retrieve some basic project info (such as icons' names) and place it into request
        ActionsHelper.retrieveAndStoreBasicProjectInfo(request, verification.getProject(), messages);

        // Place a string that represents "my" current role(s) into the request
        ActionsHelper.retrieveAndStoreMyRole(request, getResources(request));

        // Retrieve the submitter id and place it into request
        ActionsHelper.retrieveAndStoreSubmitterInfo(request, upload);

        // Put review type into the request
        request.setAttribute("reviewType", "AutoScreening");
        // Specify that Expand All / Collapse All links are not needed
        request.setAttribute("noExpandCollapse", new Boolean(true));

        // Obtain Screening Manager instance
        ScreeningManager screeningManager = ActionsHelper.createScreeningManager(request);

        // Retrieve the automated screening results
        ScreeningTask screeningTask = screeningManager.getScreeningDetails(upload.getId());
        ScreeningResult[] screeningResults = screeningTask.getAllScreeningResults();

        // Group the results according to severity statuses and screening responses
        // Finally the results are grouped into the map where
        // the keys are the ids of severity statuses and the values are another maps,
        // in which keys are ids of screening responses and the values are screening results.
        Map screeningResultsMap = new TreeMap();
        for (int i = 0; i < screeningResults.length; i++) {
            ResponseSeverity responseSeverity = screeningResults[i].getScreeningResponse().getResponseSeverity();
            Long responseSeverityId = new Long(responseSeverity.getId());
            Long screeningResponseId = new Long(screeningResults[i].getScreeningResponse().getId());
            Map innerMap;
            if (screeningResultsMap.containsKey(responseSeverityId)) {
                innerMap = (Map) screeningResultsMap.get(responseSeverityId);
            } else {
                innerMap = new TreeMap();
                screeningResultsMap.put(responseSeverityId, innerMap);
            }
            if (innerMap.containsKey(screeningResponseId)) {
                ((List) innerMap.get(screeningResponseId)).add(screeningResults[i]);
            } else {
                List list = new ArrayList();
                innerMap.put(screeningResponseId, list);
                list.add(screeningResults[i]);
            }
        }
        // Store grouped results in the request
        request.setAttribute("screeningResultsMap", screeningResultsMap);

        // Return success forward
        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * This method verifies the request for ceratin conditions to be met. This includes verifying if
     * the user has specified an ID of the project he wants to perform an operation on, if the ID of
     * the project specified by user denotes existing project, and whether the user has rights to
     * perform the operation specified by <code>permission</code> parameter.
     *
     * @return an instance of the {@link CorrectnessCheckResult} class, which specifies whether the
     *         check was successful and, in the case it was, contains additional information
     *         retrieved during the check operation, which might be of some use for the calling
     *         method.
     * @param mapping
     *            action mapping.
     * @param request
     *            the http request.
     * @param permission
     *            permission to check against, or <code>null</code> if no check is requeired.
     * @throws BaseException
     *             if any error occurs.
     */
    private CorrectnessCheckResult checkForCorrectProjectId(ActionMapping mapping,
            HttpServletRequest request, String permission)
        throws BaseException {
        // Prepare bean that will be returned as the result
        CorrectnessCheckResult result = new CorrectnessCheckResult();

        if (permission == null || permission.trim().length() == 0) {
            permission = null;
        }

        // Verify that Project ID was specified and denotes correct project
        String pidParam = request.getParameter("pid");
        if (pidParam == null || pidParam.trim().length() == 0) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, permission, "Error.ProjectIdNotSpecified"));
            // Return the result of the check
            return result;
        }

        long pid;

        try {
            // Try to convert specified pid parameter to its integer representation
            pid = Long.parseLong(pidParam, 10);
        } catch (NumberFormatException nfe) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, permission, "Error.ProjectNotFound"));
            // Return the result of the check
            return result;
        }

        // Obtain an instance of Project Manager
        ProjectManager projMgr = ActionsHelper.createProjectManager(request);
        // Get Project by its id
        Project project = projMgr.getProject(pid);
        // Verify that project with given ID exists
        if (project == null) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, permission, "Error.ProjectNotFound"));
            // Return the result of the check
            return result;
        }

        // Store Project object in the result bean
        result.setProject(project);
        // Place project as attribute in the request
        request.setAttribute("project", project);

        // Gather the roles the user has for current request
        AuthorizationHelper.gatherUserRoles(request, pid);

        // If permission parameter was not null or empty string ...
        if (permission != null) {
            // ... verify that this permission is granted for currently logged in user
            if (!AuthorizationHelper.hasUserPermission(request, permission)) {
                result.setForward(ActionsHelper.produceErrorReport(
                        mapping, getResources(request), request, permission, "Error.NoPermission"));
                // Return the result of the check
                return result;
            }
        }

        return result;
    }

    /**
     * This method verifies the request for ceratin conditions to be met. This includes verifying if
     * the user has specified an ID of the upload he wants to perform an operation on (most often
     * &#x96; to download), and whether the ID of the upload specified by user denotes existing
     * upload.
     *
     * @return an instance of the {@link CorrectnessCheckResult} class, which specifies whether the
     *         check was successful and, in the case it was, contains additional information
     *         retrieved during the check operation, which might be of some use for the calling
     *         method.
     * @param mapping
     *            action mapping.
     * @param request
     *            the http request.
     * @param permission
     *            permission to check against, or <code>null</code> if no check is requeired.
     * @throws IllegalArgumentException
     *             if any of the parameters are <code>null</code>, or if
     *             <code>errorMessageKey</code> parameter is an empty string.
     * @throws BaseException
     *             if any error occurs.
     */
    private CorrectnessCheckResult checkForCorrectUploadId(ActionMapping mapping,
            HttpServletRequest request, String errorMessageKey)
        throws BaseException {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(mapping, "mapping");
        ActionsHelper.validateParameterNotNull(request, "request");
        ActionsHelper.validateParameterStringNotEmpty(errorMessageKey, "errorMessageKey");

        // Prepare bean that will be returned as the result
        CorrectnessCheckResult result = new CorrectnessCheckResult();

        // Verify that Upload ID was specified and denotes correct upload
        String uidParam = request.getParameter("uid");
        if (uidParam == null || uidParam.trim().length() == 0) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, errorMessageKey, "Error.UploadIdNotSpecified"));
            // Return the result of the check
            return result;
        }

        long uid;

        try {
            // Try to convert specified uid parameter to its integer representation
            uid = Long.parseLong(uidParam, 10);
        } catch (NumberFormatException nfe) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, errorMessageKey, "Error.UploadNotFound"));
            // Return the result of the check
            return result;
        }

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        // Get Upload by its ID
        Upload upload = upMgr.getUpload(uid);
        // Verify that upload with given ID exists
        if (upload == null) {
            result.setForward(ActionsHelper.produceErrorReport(
                    mapping, getResources(request), request, errorMessageKey, "Error.UploadNotFound"));
            // Return the result of the check
            return result;
        }

        // Store Upload object in the result bean
        result.setUpload(upload);

        // Obtain an instance of Project Manager
        ProjectManager projMgr = ActionsHelper.createProjectManager(request);
        // Get a Project for this upload
        Project project = projMgr.getProject(upload.getProject());

        // Store Project object in the result bean
        result.setProject(project);
        // Place project as attribute in the request
        request.setAttribute("project", project);

        // Gather the roles the user has for current request
        AuthorizationHelper.gatherUserRoles(request, project.getId());

        return result;
    }

    private static int[] getPhaseStatusCodes(Phase[] phases, long currentTime) {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(phases, "phases");

        int[] statusCodes = new int[phases.length];
        for (int i = 0; i < phases.length; ++i) {
            // Get a Phase for the current iteration
            Phase phase = phases[i];
            String phaseStatus = phase.getPhaseStatus().getName();
            if (phaseStatus.equalsIgnoreCase("Scheduled")) {
                statusCodes[i] = 0; // Scheduled, not yet open, nothing will be displayed
            } else if (phaseStatus.equalsIgnoreCase("Closed")) {
                statusCodes[i] = 1; // Closed
            } else if (phaseStatus.equalsIgnoreCase("Open")) {
                long phaseTime = phase.calcEndDate().getTime();

                if (currentTime > phaseTime) {
                    statusCodes[i] = 4; // Late
                } else if (currentTime + (2 * 60 * 60 * 1000) > phaseTime) {
                    statusCodes[i] = 3; // Closing
                } else {
                    statusCodes[i] = 2; // Open
                }
            }
        }
        return statusCodes;
    }

    /**
     * This static method
     *
     * @return
     * @param deliverables
     * @param activePhases
     * @param currentTime
     */
    private static int[] getDeliverableStatusCodes(Deliverable[] deliverables,
            Phase[] activePhases, long currentTime) {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(deliverables, "deliverables");
        ActionsHelper.validateParameterNotNull(activePhases, "activePhases");

        int[] statusCodes = new int[deliverables.length];
        for (int i = 0; i < deliverables.length; ++i) {
            // Get a Deliverable for the current iteration
            Deliverable deliverable = deliverables[i];
            if (deliverable.isComplete()) {
                statusCodes[i] = 0;
            } else {
                Phase phase = ActionsHelper.getPhaseForDeliverable(activePhases, deliverable);

                long deliverableTime = phase.calcEndDate().getTime();
                if (currentTime > deliverableTime) {
                    statusCodes[i] = 2; // Late
                } else if (currentTime + (2 * 60 * 60 * 1000) > deliverableTime) {
                    statusCodes[i] = 1; // Deadline near
                } else {
                    statusCodes[i] = 0;
                }
            }
        }
        return statusCodes;
    }

    /**
     * This static method analyzes the array of deliverables and generates links for them.
     *
     * @return an array of links. If some deliverable does not have its appropriate link, then that
     *         entry in the returned array will be empty (containing <code>null</code> value).
     * @param request
     *            an <code>HttpServletRequest</code> object.
     * @param deliverables
     *            an array of deliverables to generate the links for.
     * @param phases
     *            an array of all phases for the current project.
     * @throws IllegalArgumentException
     *             if any of the parameters are <code>null</code>.
     * @throws BaseException
     *             if any error occurs.
     */
    private static String[] generateDeliverableLinks(HttpServletRequest request,
            Deliverable[] deliverables, Phase[] phases)
        throws BaseException {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(request, "request");
        ActionsHelper.validateParameterNotNull(deliverables, "deliverables");
        ActionsHelper.validateParameterNotNull(phases, "phases");

        String[] links = new String[deliverables.length];

        ScorecardType[] allScorecardTypes = null;

        for (int i = 0; i < deliverables.length; ++i) {
            // Get a Deliverable for the current iteration
            Deliverable deliverable = deliverables[i];
            String delivName = deliverable.getName();
            if (delivName.equalsIgnoreCase(Constants.SUBMISSION_DELIVERABLE_NAME)) {
                links[i] = "UploadSubmission.do?method=uploadSubmission&pid=" + deliverable.getProject();
            } else if (delivName.equalsIgnoreCase(Constants.SCREENING_DELIVERABLE_NAME)) {
/* This is commented out until Deliverable Management is fixed  TODO: Uncomment this block
                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Screening"),
                        deliverable.getSubmission(), deliverable.getResource(), false);

                if (review == null) {
                    links[i] = "CreateScreening.do?method=createScreening&sid=" +
                            deliverable.getSubmission().longValue();
                } else if (!review.isCommitted()) {
                    links[i] = "EditScreening.do?method=editScreening&rid=" + review.getId();
                } else {
                    links[i] = "ViewScreening.do?method=viewScreening&rid=" + review.getId();
                }
*/
            } else if (delivName.equalsIgnoreCase(Constants.REVIEW_DELIVERABLE_NAME)) {
                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review"),
                        deliverable.getSubmission(), deliverable.getResource(), false);

                if (review == null) {
                    links[i] = "CreateReview.do?method=createReview&sid=" +
                            deliverable.getSubmission().longValue();
                } else if (!review.isCommitted()) {
                    links[i] = "EditReview.do?method=editReview&rid=" + review.getId();
                } else {
                    links[i] = "ViewReview.do?method=viewReview&rid=" + review.getId();
                }
            } else if (delivName.equalsIgnoreCase(Constants.ACC_TEST_CASES_DELIVERABLE_NAME) ||
                    delivName.equalsIgnoreCase(Constants.FAIL_TEST_CASES_DELIVERABLE_NAME) ||
                    delivName.equalsIgnoreCase(Constants.STRS_TEST_CASES_DELIVERABLE_NAME)) {
                links[i] = "UploadTestCase.do?method=uploadTestCase&pid=" + deliverable.getProject();
            } else if (delivName.equalsIgnoreCase(Constants.APPEAL_RESP_DELIVERABLE_NAME)) {
                // TODO: Assign links for Appeal Responses
            } else if (delivName.equalsIgnoreCase(Constants.AGGREGATION_DELIVERABLE_NAME)) {
/*                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review"),
                        deliverable.getSubmission(), deliverable.getResource(), false);

                if (review != null) {
                    if (!review.isCommitted()) {
                        links[i] = "EditAggregation.do?method=editAggregation&rid=" + review.getId();
                    } else {
                        links[i] = "ViewAggregation.do?method=viewAggregation&rid=" + review.getId();
                    }
                }*/
            } else if (delivName.equalsIgnoreCase(Constants.AGGREGATION_REV_DELIVERABLE_NAME)) {
/*                Phase phase = ActionsHelper.getPhase(phases, false, Constants.AGGREGATION_PHASE_NAME);
                Resource[] aggregator =
                    ActionsHelper.getAllResourcesForPhase(ActionsHelper.createResourceManager(request), phase);

                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review"),
                        deliverable.getSubmission(), aggregator[0].getId(), true);

                if (review == null) {
                    continue;
                }

                Comment myComment = null;

                for (int j = 0; j < review.getNumberOfComments(); ++j) {
                    if (review.getComment(j).getAuthor() == deliverable.getResource()) {
                        myComment = review.getComment(j);
                        break;
                    }
                }

                if (myComment == null ||
                        !("Approved".equalsIgnoreCase((String) myComment.getExtraInfo()) ||
                        "Rejected".equalsIgnoreCase((String) myComment.getExtraInfo()))) {
                    links[i] = "EditAggregationReview.do?method=editAggregationReview&rid=" + review.getId();
                }*/
            } else if (delivName.equalsIgnoreCase(Constants.FINAL_FIX_DELIVERABLE_NAME)) {
                links[i] = "UploadFinalFix.do?method=uploadFinalFix&pid=" + deliverable.getProject();
            } else if (delivName.equalsIgnoreCase(Constants.SCORECARD_COMM_DELIVERABLE_NAME)) {
/*
                Phase phase = ActionsHelper.getPhase(phases, false, Constants.AGGREGATION_PHASE_NAME);
                Resource[] aggregator =
                    ActionsHelper.getAllResourcesForPhase(ActionsHelper.createResourceManager(request), phase);

                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review"),
                        deliverable.getSubmission(), aggregator[0].getId(), true);

                if (review == null) {
                    continue;
                }

                Comment myComment = null;

                for (int j = 0; j < review.getNumberOfComments(); ++j) {
                    if (review.getComment(j).getAuthor() == deliverable.getResource()) {
                        myComment = review.getComment(j);
                        break;
                    }
                }

                if (myComment == null ||
                        !("Approved".equalsIgnoreCase((String) myComment.getExtraInfo()) ||
                        "Rejected".equalsIgnoreCase((String) myComment.getExtraInfo()))) {
                    links[i] = "EditAggregationReview.do?method=editAggregationReview&rid=" + review.getId();
                }
*/
            } else if (delivName.equalsIgnoreCase(Constants.FINAL_REVIEW_DELIVERABLE_NAME)) {
/*                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Review"),
                        deliverable.getSubmission(), deliverable.getResource(), false);

                if (review != null) {
                    if (!review.isCommitted()) {
                        links[i] = "EditFinalReview.do?method=editFinalReview&rid=" + review.getId();
                    } else {
                        links[i] = "ViewFinalReview.do?method=viewFinalReview&rid=" + review.getId();
                    }
                }*/
            } else if (delivName.equalsIgnoreCase(Constants.APPROVAL_DELIVERABLE_NAME)) {
                if (allScorecardTypes == null) {
                    // Get all scorecard types
                    allScorecardTypes = ActionsHelper.createScorecardManager(request).getAllScorecardTypes();
                }

                Review review = findReviewForSubmission(ActionsHelper.createReviewManager(request),
                        ActionsHelper.findScorecardTypeByName(allScorecardTypes, "Client Review"),
                        deliverable.getSubmission(), deliverable.getResource(), false);

                if (review == null) {
                    links[i] = "CreateApproval.do?method=createApproval&sid=" +
                            deliverable.getSubmission().longValue();
                } else if (!review.isCommitted()) {
                    links[i] = "EditApproval.do?method=editApproval&rid=" + review.getId();
                } else {
                    links[i] = "ViewApproval.do?method=viewApproval&rid=" + review.getId();
                }
            }
        }

        return links;
    }

    private static String[] getDeliverableUserIds(ResourceManager manager, Deliverable[] deliverables)
        throws BaseException {
        List resourceIds = new ArrayList();

        for (int i = 0; i < deliverables.length; ++i) {
            resourceIds.add(new Long(deliverables[i].getResource()));
        }

        if (resourceIds.isEmpty()) {
            return new String[0];
        }

        Filter filter = new InFilter("resource.resource_id", resourceIds);

        Resource[] resources = manager.searchResources(filter);

        String[] ids = new String[deliverables.length];

        for (int i = 0; i < deliverables.length; ++i) {
            for (int j = 0; j < resources.length; ++j) {
                if (resources[j].getId() == deliverables[i].getResource()) {
                    ids[i] = (String) resources[j].getProperty("External Reference ID");
                    break;
                }
            }
        }

        return ids;
    }

    private static String[] getDeliverableSubmissionUserIds(HttpServletRequest request, Deliverable[] deliverables)
        throws BaseException {
        List submissionIds = new ArrayList();

        for (int i = 0; i < deliverables.length; ++i) {
            if (deliverables[i].getSubmission() != null) {
                submissionIds.add(deliverables[i].getSubmission());
            }
        }

        if (submissionIds.isEmpty()) {
            return new String[0];
        }

        Filter filterSubmissions = new InFilter("submission_id", submissionIds);

        // Obtain an instance of Upload Manager
        UploadManager upMgr = ActionsHelper.createUploadManager(request);
        Submission[] submissions = upMgr.searchSubmissions(filterSubmissions);

        List resourceIds = new ArrayList();

        for (int i = 0; i < submissions.length; ++i) {
            resourceIds.add(new Long(submissions[i].getUpload().getOwner()));
        }

        Filter filterResources = new InFilter("resource.resource_id", resourceIds);

        // Obtain an instance of Resource Manager
        ResourceManager resMgr = ActionsHelper.createResourceManager(request);
        Resource[] resources = resMgr.searchResources(filterResources);

        String[] ids = new String[deliverables.length];

        for (int i = 0; i < deliverables.length; ++i) {
            if (deliverables[i].getSubmission() == null) {
                continue;
            }
            long deliverableId = deliverables[i].getSubmission().longValue();
            for (int j = 0; j < submissions.length; ++j) {
                if (submissions[j].getId() != deliverableId) {
                    continue;
                }
                long submissionOwnerId = submissions[j].getUpload().getOwner();
                for (int k = 0; k < resources.length; ++k) {
                    if (resources[k].getId() == submissionOwnerId) {
                        ids[i] = (String) resources[k].getProperty("External Reference ID");
                        break;
                    }
                }
                break;
            }
        }

        return ids;
    }

    /**
     * This static method finds and returns a review of specified scorecard template type for
     * specified submission ID and made by specified resource.
     *
     * @return found review or <code>null</code> if no review has been found.
     * @param manager
     *            an instance of <code>ReviewManager</code> class that retrieves a review from the
     *            database.
     * @param scorecardType
     *            a scorecard template type that found review should have.
     * @param submissionId
     *            an ID of the submission which the review was made for.
     * @param resourceId
     *            an ID of the resource who made (created) the review.
     * @param complete
     *            specifies whether retrieved review should have all infomration (like all items and
     *            their comments).
     * @throws IllegalArgumentException
     *             if <code>scorecardType</code> or <code>submissionId</code> parameters are
     *             <code>null</code>, or if <code>submissionId</code> or
     *             <code>resourceId</code> parameters contain negative value or zero.
     * @throws ReviewManagementException
     *             if any error occurs during review search or retrieval.
     */
    private static Review findReviewForSubmission(ReviewManager manager,
            ScorecardType scorecardType, Long submissionId, long resourceId, boolean complete)
        throws ReviewManagementException {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(manager, "manager");
        ActionsHelper.validateParameterNotNull(scorecardType, "scorecardType");
        ActionsHelper.validateParameterNotNull(submissionId, "submissionId");
        ActionsHelper.validateParameterPositive(submissionId.longValue(), "submissionId");
        ActionsHelper.validateParameterPositive(resourceId, "resourceId");

        Filter filterSubmission = new EqualToFilter("submission", submissionId);
        Filter filterScorecard = new EqualToFilter("scorecardType", new Long(scorecardType.getId()));
        Filter filterReviewer = new EqualToFilter("reviewer", new Long (resourceId));

        Filter filter = new AndFilter(Arrays.asList(
                new Filter[] {filterSubmission, filterScorecard, filterReviewer}));

        // Get a review(s) that pass filter
        Review[] reviews = manager.searchReviews(filter, complete);
        // Return the first found review if any, or null
        return (reviews.length != 0) ? reviews[0] : null;
    }
}

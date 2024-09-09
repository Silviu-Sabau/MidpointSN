/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.notifications.impl;

import java.util.List;
import javax.xml.datatype.Duration;

import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.SchemaException;

import com.evolveum.midpoint.util.exception.SystemException;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.cases.api.CaseManager;
import com.evolveum.midpoint.cases.api.events.CaseEventCreationListener;
import com.evolveum.midpoint.cases.api.events.WorkItemAllocationChangeOperationInfo;
import com.evolveum.midpoint.cases.api.events.WorkItemOperationInfo;
import com.evolveum.midpoint.cases.api.events.WorkItemOperationSourceInfo;
import com.evolveum.midpoint.model.common.expression.ExpressionProfileManager;
import com.evolveum.midpoint.notifications.api.events.SimpleObjectRef;
import com.evolveum.midpoint.notifications.impl.events.*;
import com.evolveum.midpoint.notifications.impl.util.EventHelper;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.config.ConfigurationItemOrigin;
import com.evolveum.midpoint.schema.config.EventHandlerConfigItem;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Listener that accepts events generated by workflow module. These events are related to processes and work items.
 *
 * TODO what about tasks? Should the task (wfTask) be passed to the notification module?
 */
@Component
public class CaseEventCreationListenerImpl implements CaseEventCreationListener {

    private static final Trace LOGGER = TraceManager.getTrace(CaseEventCreationListenerImpl.class);

    @Autowired private LightweightIdentifierGenerator identifierGenerator;
    @Autowired private EventHelper eventHelper;
    @Autowired private ExpressionProfileManager expressionProfileManager;

    // CaseManager is not required, because e.g. within model-test and model-intest we have no workflows.
    // However, during normal operation, it is expected to be available.
    @Autowired(required = false) private CaseManager caseManager;

    @PostConstruct
    public void init() {
        if (caseManager != null) {
            caseManager.registerCaseEventCreationListener(this);
        } else {
            LOGGER.warn("CaseManager not present, notifications for workflows will not be enabled.");
        }
    }

    //region Process-level notifications
    @Override
    public void onCaseOpening(CaseType aCase, Task task, OperationResult result) {
        CaseEventImpl event = new CaseEventImpl(identifierGenerator, ChangeType.ADD, aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onCaseClosing(CaseType aCase, Task task, OperationResult result) {
        CaseEventImpl event = new CaseEventImpl(identifierGenerator, ChangeType.DELETE, aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }
    //endregion

    //region WorkItem-level notifications
    @Override
    public void onWorkItemCreation(ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemLifecycleEventImpl(
                identifierGenerator,
                ChangeType.ADD,
                workItem,
                SimpleObjectRefImpl.create(assignee),
                null,
                null,
                null,
                aCase.getApprovalContext(),
                aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onWorkItemClosing(ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            WorkItemOperationInfo operationInfo, WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemLifecycleEventImpl(identifierGenerator, ChangeType.DELETE, workItem,
                SimpleObjectRefImpl.create(assignee),
                getInitiator(sourceInfo), operationInfo, sourceInfo, aCase.getApprovalContext(), aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onWorkItemCustomEvent(
            ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            @NotNull WorkItemNotificationActionType notificationAction, WorkItemEventCauseInformationType cause,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemCustomEventImpl(
                identifierGenerator, ChangeType.ADD, workItem,
                SimpleObjectRefImpl.create(assignee),
                new WorkItemOperationSourceInfo(null, cause, notificationAction),
                aCase.getApprovalContext(), aCase);
        initializeWorkflowEvent(event, aCase);

        EventHandlerConfigItem customHandlerCI;
        ExpressionProfile customHandlerProfile;
        EventHandlerType customHandler = notificationAction.getHandler();
        if (customHandler != null) {
            customHandlerCI =
                    EventHandlerConfigItem.of(
                            customHandler,
                            ConfigurationItemOrigin.undeterminedSafe());
            try {
                customHandlerProfile = expressionProfileManager.getProfileForCustomWorkflowNotifications(result);
            } catch (SchemaException | ConfigurationException e) {
                throw SystemException.unexpected(e); // FIXME later
            }
        } else {
            customHandlerCI = null;
            customHandlerProfile = null;
        }
        eventHelper.processEvent(event, customHandlerCI, customHandlerProfile, task, result);
    }

    @Override
    public void onWorkItemAllocationChangeCurrentActors(@NotNull CaseWorkItemType workItem,
            @NotNull WorkItemAllocationChangeOperationInfo operationInfo,
            @Nullable WorkItemOperationSourceInfo sourceInfo,
            Duration timeBefore, CaseType aCase,
            Task task, OperationResult result) {
        checkOids(operationInfo.getCurrentActors());
        for (ObjectReferenceType currentActor : operationInfo.getCurrentActors()) {
            onWorkItemAllocationModifyDelete(currentActor, workItem, operationInfo, sourceInfo, timeBefore, aCase, task, result);
        }
    }

    @Override
    public void onWorkItemAllocationChangeNewActors(@NotNull CaseWorkItemType workItem,
            @NotNull WorkItemAllocationChangeOperationInfo operationInfo,
            @Nullable WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        Validate.notNull(operationInfo.getNewActors());

        checkOids(operationInfo.getCurrentActors());
        checkOids(operationInfo.getNewActors());
        for (ObjectReferenceType newActor : operationInfo.getNewActors()) {
            onWorkItemAllocationAdd(newActor, workItem, operationInfo, sourceInfo, aCase, task, result);
        }
    }

    private void checkOids(List<ObjectReferenceType> refs) {
        refs.forEach(r -> Validate.notNull(r.getOid(), "No OID in actor object reference " + r));
    }

    private void onWorkItemAllocationAdd(ObjectReferenceType newActor, @NotNull CaseWorkItemType workItem,
            @Nullable WorkItemOperationInfo operationInfo, @Nullable WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemAllocationEventImpl event = new WorkItemAllocationEventImpl(identifierGenerator, ChangeType.ADD, workItem,
                SimpleObjectRefImpl.create(newActor),
                getInitiator(sourceInfo), operationInfo, sourceInfo,
                aCase.getApprovalContext(), aCase, null);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    private SimpleObjectRef getInitiator(WorkItemOperationSourceInfo sourceInfo) {
        return sourceInfo != null ?
                SimpleObjectRefImpl.create(sourceInfo.getInitiatorRef()) : null;
    }

    private void onWorkItemAllocationModifyDelete(ObjectReferenceType currentActor, @NotNull CaseWorkItemType workItem,
            @Nullable WorkItemOperationInfo operationInfo, @Nullable WorkItemOperationSourceInfo sourceInfo,
            Duration timeBefore, CaseType aCase,
            Task task, OperationResult result) {
        WorkItemAllocationEventImpl event = new WorkItemAllocationEventImpl(identifierGenerator,
                timeBefore != null ? ChangeType.MODIFY : ChangeType.DELETE, workItem,
                SimpleObjectRefImpl.create(currentActor),
                getInitiator(sourceInfo), operationInfo, sourceInfo,
                aCase.getApprovalContext(), aCase, timeBefore);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }
    //endregion

    private void initializeWorkflowEvent(CaseManagementEventImpl event, CaseType aCase) {
        event.setRequester(SimpleObjectRefImpl.create(aCase.getRequestorRef()));
        event.setRequestee(SimpleObjectRefImpl.create(aCase.getObjectRef()));
        // TODO what if requestee is yet to be created?
    }
}

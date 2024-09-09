/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.ucf.api;

/**
 *
 */
public interface UcfExpressionEvaluatorAware {

    @SuppressWarnings("unused")
    UcfExpressionEvaluator getUcfExpressionEvaluator();

    void setUcfExpressionEvaluator(UcfExpressionEvaluator evaluator);
}

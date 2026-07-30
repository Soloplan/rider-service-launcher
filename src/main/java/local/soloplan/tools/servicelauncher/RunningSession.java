//-----------------------------------------------------------------------
// <copyright file="RunningSession.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.execution.process.ProcessHandler;

/**
 * Represents a running session.
 */
record RunningSession(ProcessHandler handler, ExecutionMode mode)
{
}

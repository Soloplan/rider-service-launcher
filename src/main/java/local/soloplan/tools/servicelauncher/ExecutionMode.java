//-----------------------------------------------------------------------
// <copyright file="ExecutionMode.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;

/**
 * Defines an execution mode.
 */
enum ExecutionMode
{
  RUN
  {
    /** {@inheritDoc} */
    @Override
    Executor executor()
    {
      return DefaultRunExecutor.getRunExecutorInstance();
    }
  },
  DEBUG
  {
    /** {@inheritDoc} */
    @Override
    Executor executor()
    {
      return DefaultDebugExecutor.getDebugExecutorInstance();
    }
  };

  /**
   * Returns the result of executor.
   *
   * @return the executor result
   */
  abstract Executor executor();

  /**
   * Returns the result of from executor id.
   *
   * @param executorId the executor id
   * @return the from executor id result
   */
  static ExecutionMode fromExecutorId(String executorId)
  {
    String debugExecutorId = DefaultDebugExecutor.getDebugExecutorInstance().getId();
    return debugExecutorId.equals(executorId) ? DEBUG : RUN;
  }
}

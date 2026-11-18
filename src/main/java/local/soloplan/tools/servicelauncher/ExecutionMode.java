//-----------------------------------------------------------------------
// <copyright file="ExecutionMode.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
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
    Executor executor() throws ExecutionException
    {
      Executor executor = ExecutorRegistry.getInstance().getExecutorById(DEBUG_EXECUTOR_ID);
      if (executor == null)
      {
        throw new ExecutionException("The IDE debug executor is unavailable.");
      }
      return executor;
    }
  };

  private static final String DEBUG_EXECUTOR_ID = "Debug";

  /**
   * Resolves the IDE executor for this mode.
   *
   * @return the registered executor
   * @throws ExecutionException if the executor is unavailable
   */
  abstract Executor executor() throws ExecutionException;

  /**
   * Returns the result of from executor id.
   *
   * @param executorId the executor id
   * @return the from executor id result
   */
  static ExecutionMode fromExecutorId(String executorId)
  {
    return DEBUG_EXECUTOR_ID.equals(executorId) ? DEBUG : RUN;
  }
}

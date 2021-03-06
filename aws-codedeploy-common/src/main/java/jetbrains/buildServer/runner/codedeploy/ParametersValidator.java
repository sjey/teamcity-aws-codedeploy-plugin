/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.runner.codedeploy;

import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.runner.codedeploy.CodeDeployConstants.*;
import static jetbrains.buildServer.runner.codedeploy.CodeDeployUtil.*;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vbedrosova
 */
final class ParametersValidator {
  /**
   * Must be used for parameters validation during the build
   * Returns map from parameter name to invalidity reason
   */
  @NotNull
  static Map<String, String> validateRuntime(@NotNull Map<String, String> runnerParams, @NotNull Map<String, String> configParams, @NotNull File checkoutDir) {
    final Map<String, String> invalids = new HashMap<String, String>(validate(runnerParams, true));

    if (!invalids.containsKey(REVISION_PATHS_PARAM) && isUploadStepEnabled(runnerParams)) {
      final String revisionPath = getReadyRevision(runnerParams.get(REVISION_PATHS_PARAM));
      if (revisionPath != null && !FileUtil.resolvePath(checkoutDir, revisionPath).exists()) {
        invalids.put(REVISION_PATHS_PARAM, REVISION_PATHS_LABEL + " " + revisionPath + " doesn't exist");
      }
    }

    if (isDeploymentWaitEnabled(runnerParams)) {
      final String waitIntervalSec = configParams.get(WAIT_POLL_INTERVAL_SEC_CONFIG_PARAM);
      if (StringUtil.isNotEmpty(waitIntervalSec)) {
        validatePositiveInteger(invalids, waitIntervalSec, WAIT_POLL_INTERVAL_SEC_CONFIG_PARAM, WAIT_POLL_INTERVAL_SEC_CONFIG_PARAM, true);
      }
    }

    return Collections.unmodifiableMap(invalids);
  }

  /**
   * Returns map from parameter name to invalidity reason
   */
  @NotNull
  static Map<String, String> validateSettings(@NotNull Map<String, String> params) {
    return validate(params, false);
  }

  private static Map<String, String> validate(@NotNull Map<String, String> runnerParams, boolean runtime) {
    final Map<String, String> invalids = new HashMap<String, String>();

    invalids.putAll(AWSCommonParams.validate(runnerParams, !runtime));

    boolean uploadStepEnabled = false;
    boolean registerStepEnabled = false;
    boolean deployStepEnabled = false;

    final String deploymentSteps = runnerParams.get(DEPLOYMENT_STEPS_PARAM);
    if (StringUtil.isEmptyOrSpaces(deploymentSteps)) {
      invalids.put(DEPLOYMENT_STEPS_PARAM, DEPLOYMENT_STEPS_LABEL + " mustn't be empty");
    } else {
      uploadStepEnabled = isUploadStepEnabled(runnerParams);
      registerStepEnabled = isRegisterStepEnabled(runnerParams);
      deployStepEnabled = isDeployStepEnabled(runnerParams);

      if (!uploadStepEnabled && !registerStepEnabled && !deployStepEnabled) {
        invalids.put(DEPLOYMENT_STEPS_PARAM, DEPLOYMENT_STEPS_LABEL + " has unexpected value " + deploymentSteps);
      }
    }

    if (uploadStepEnabled) {
      final String revisionPaths = runnerParams.get(REVISION_PATHS_PARAM);
      if (StringUtil.isEmptyOrSpaces(revisionPaths)) {
        invalids.put(REVISION_PATHS_PARAM, REVISION_PATHS_LABEL + " mustn't be empty");
      } else if (!isReference(revisionPaths, runtime)) {
        final String readyRevision = getReadyRevision(revisionPaths);
        if (readyRevision == null) {
          if (getRevisionPathMappings(revisionPaths).isEmpty()) {
            invalids.put(REVISION_PATHS_PARAM, REVISION_PATHS_LABEL + " has unexpected value, " + REVISION_PATHS_NOTE);
          }
        }
      }
    }

    if (uploadStepEnabled || registerStepEnabled || deployStepEnabled) {
      final String s3BucketName = runnerParams.get(S3_BUCKET_NAME_PARAM);
      if (StringUtil.isEmptyOrSpaces(s3BucketName)) {
        invalids.put(S3_BUCKET_NAME_PARAM, S3_BUCKET_NAME_LABEL + " mustn't be empty");
      } else if (s3BucketName.contains("/")) {
        invalids.put(S3_BUCKET_NAME_PARAM, S3_BUCKET_NAME_LABEL + " mustn't contain / characters. For addressing folders use " + S3_OBJECT_KEY_LABEL + " parameter");
      }

      final String s3ObjectKey = runnerParams.get(S3_OBJECT_KEY_PARAM);
      if (StringUtil.isEmptyOrSpaces(s3ObjectKey)) {
        if (!uploadStepEnabled) {
          invalids.put(S3_OBJECT_KEY_PARAM, S3_OBJECT_KEY_LABEL + " mustn't be empty");
        }
      } else {
        validateS3Key(invalids, s3ObjectKey, S3_OBJECT_KEY_PARAM, S3_OBJECT_KEY_LABEL, runtime);
        if (registerStepEnabled || deployStepEnabled) {
          validateBundleType(invalids, s3ObjectKey, S3_OBJECT_KEY_PARAM, S3_OBJECT_KEY_LABEL, runtime);
        }
      }
    }

    if (registerStepEnabled || deployStepEnabled) {
      if (StringUtil.isEmptyOrSpaces(runnerParams.get(APP_NAME_PARAM))) {
        invalids.put(APP_NAME_PARAM, APP_NAME_LABEL + " mustn't be empty");
      }
    }

    if (deployStepEnabled) {
      if (StringUtil.isEmptyOrSpaces(runnerParams.get(DEPLOYMENT_GROUP_NAME_PARAM))) {
        invalids.put(DEPLOYMENT_GROUP_NAME_PARAM, DEPLOYMENT_GROUP_NAME_LABEL + " mustn't be empty");
      }

      if (isDeploymentWaitEnabled(runnerParams)) {
        final String waitTimeoutSec = runnerParams.get(WAIT_TIMEOUT_SEC_PARAM);
        if (StringUtil.isEmptyOrSpaces(waitTimeoutSec)) {
          invalids.put(WAIT_TIMEOUT_SEC_PARAM, WAIT_TIMEOUT_SEC_LABEL + " mustn't be empty");
        } else {
          validatePositiveInteger(invalids, waitTimeoutSec, WAIT_TIMEOUT_SEC_PARAM, WAIT_TIMEOUT_SEC_LABEL, runtime);
        }
      }
    }
    return invalids;
  }

  private static void validatePositiveInteger(@NotNull Map<String, String> invalids, @NotNull String param, @NotNull String key, @NotNull String name, boolean runtime) {
    if (!isReference(param, runtime)) {
      try {
        final int i = Integer.parseInt(param);
        if (i <= 0) {
          invalids.put(key, name + " must be a positive integer value");
        }
      } catch (NumberFormatException e) {
        invalids.put(key, name + " must be a positive integer value");
      }
    }
  }

  private static void validateS3Key(@NotNull Map<String, String> invalids, @NotNull String param, @NotNull String key, @NotNull String name, boolean runtime) {
    if (!isReference(param, runtime)) {
      if (!param.matches("[a-zA-Z_0-9!\\-\\.*'()/]*")) {
        invalids.put(key, name + " must contain only safe characters");
      }
    }
  }

  private static void validateBundleType(@NotNull Map<String, String> invalids, @NotNull String param, @NotNull String key, @NotNull String name, boolean runtime) {
    if (!isReference(param, runtime)) {
      if (null == AWSUtil.getBundleType(param)) {
        invalids.put(key, name + " provides invalid bundle type, supported bundle types are .zip, .tar and .tar.gz");
      }
    }
  }

  private static boolean isReference(@NotNull String param, boolean runtime) {
    return ReferencesResolverUtil.containsReference(param, new String[]{}, true) && !runtime;
  }
}

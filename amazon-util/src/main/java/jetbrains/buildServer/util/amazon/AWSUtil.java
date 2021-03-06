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

package jetbrains.buildServer.util.amazon;

import com.amazonaws.services.codedeploy.model.BundleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vbedrosova
 */
public final class AWSUtil {
  @Nullable
  public static String getBundleType(@NotNull String revision) {
    if (revision.endsWith(".zip")) return BundleType.Zip.name();
    if (revision.endsWith(".tar")) return BundleType.Tar.name();
    if (revision.endsWith(".tar.gz")) return BundleType.Tgz.name();
    return null;
  }
}

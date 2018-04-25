/*
 * Copyright 2018 Danny Althoff
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.dynamicfiles.projects.maven.distributionbundleplugin.bundler.nativeapp.windows.x64;

/**
 *
 * @author FibreFoX
 */
public enum InternalParameterEnum {

    /**
     * As this bundler contains some magic, while creating the native bundle, it searches for previous
     * "java-app" mojo executions to gain some knowledge for the native binary to bootstrap the launch.
     */
    USE_JAVAAPP_EXECUTION_ID("useJavaAppExecutionId", Boolean.class);

    private final String parameterName;
    private final Class typeToConvertTo;

    private InternalParameterEnum(String parameterName, Class typeToConvertTo) {
        this.parameterName = parameterName;
        this.typeToConvertTo = typeToConvertTo;
    }

    public String getParameterName() {
        return parameterName;
    }

    public Class getTypeToConvertTo() {
        return typeToConvertTo;
    }

}

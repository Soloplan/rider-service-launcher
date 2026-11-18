//-----------------------------------------------------------------------
// <copyright file="build.gradle.kts" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

plugins {
  java
  id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "local.soloplan.tools"
version = "0.7.0"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    val localRiderPath = providers.gradleProperty("localRiderPath").orNull
    if (localRiderPath == null) {
      rider("2026.3") {
        useInstaller = false
      }
    } else {
      local(localRiderPath)
    }
  }
  testImplementation("junit:junit:4.13.2")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.add("-Xlint:all,-serial")
}

intellijPlatform {
  buildSearchableOptions = false

  pluginConfiguration {
    ideaVersion {
      sinceBuild = "263"
      untilBuild = provider { null }
    }
  }
}

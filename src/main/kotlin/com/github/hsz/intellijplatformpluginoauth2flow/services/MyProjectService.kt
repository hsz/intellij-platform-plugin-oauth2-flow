package com.github.hsz.intellijplatformpluginoauth2flow.services

import com.intellij.openapi.project.Project
import com.github.hsz.intellijplatformpluginoauth2flow.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}

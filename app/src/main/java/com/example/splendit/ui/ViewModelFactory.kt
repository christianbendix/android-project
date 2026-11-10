package com.example.splendit.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.splendit.SplenditApplication
import com.example.splendit.data.GroupRepository

/** Lets each ViewModel's Factory pull the shared repository out of the Application. */
val CreationExtras.groupRepository: GroupRepository
    get() = (this[APPLICATION_KEY] as SplenditApplication).container.groupRepository

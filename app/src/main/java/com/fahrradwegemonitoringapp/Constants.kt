/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("Constants")

package com.fahrradwegemonitoringapp

/**
 *  Dies ist die Anfrage ID, welche zurückgegeben wird
 *  wenn der Nutzer die Erlaubnis gegeben oder nicht gegeben hat für
 *  den Zugriff auf die Kamera, Schreibrechte und GPS des Smartphones.
 */
const val REQUEST_MULTIPLE_PERMISSIONS = 1

// Dauer des längsten möglichen Zeitfensters in ns
const val WORSTCASETIMEFRAME : Long = 720000000

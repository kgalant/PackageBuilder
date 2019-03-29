/** ========================================================================= *
 * Copyright (C)  2017, 2019 Salesforce Inc ( http://www.salesforce.com/      *
 *                            All rights reserved.                            *
 *                                                                            *
 *  @author     Stephan H. Wissel (stw) <swissel@salesforce.com>              *
 *                                       @notessensei                         *
 * @version     1.0                                                           *
 * ========================================================================== *
 *                                                                            *
 * Licensed under the  Apache License, Version 2.0  (the "License").  You may *
 * not use this file except in compliance with the License.  You may obtain a *
 * copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.       *
 *                                                                            *
 * Unless  required  by applicable  law or  agreed  to  in writing,  software *
 * distributed under the License is distributed on an  "AS IS" BASIS, WITHOUT *
 * WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.  See the *
 * License for the  specific language  governing permissions  and limitations *
 * under the License.                                                         *
 *                                                                            *
 * ========================================================================== *
 */
package com.kgal.packagebuilder;

/**
 * Result of the persistence operation for a Zip file downloaded from the
 * Salesforce Meta Data API
 *
 * @author swissel
 *
 */
public class PersistResult {

    public enum Status {
        SUCCESS(3), FAILURE(2), ABORT(1), UNDEFINED(0);
        private final int level;

        Status(final int level) {
            this.level = level;
        }

        int getLevel() {
            return this.level;
        }

    };

    private final String name;
    private boolean      done   = false;
    private Status       status = Status.UNDEFINED;

    public PersistResult(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public Status getStatus() {
        return this.status;
    }

    public boolean isDone() {
        return this.done;
    }

    public void setDone() {
        this.done = true;

    }

    public void setStatus(final Status status) {
        this.status = status;

    }

}
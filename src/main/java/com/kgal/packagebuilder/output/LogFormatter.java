/** ========================================================================= *
 * Copyright (C)  2017, 2018 Salesforce Inc ( http://www.salesforce.com/      *
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
package com.kgal.packagebuilder.output;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author swissel
 *
 */
public class LogFormatter extends Formatter {

    private final DateFormat df         = new SimpleDateFormat("hh:mm:ss.SSS");
    private boolean          appendLine = false;

    /**
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder(1000);
        String recordMessage = formatMessage(record);
        if (!this.appendLine) {
            builder.append(df.format(new Date(record.getMillis()))).append(" - ");
            builder.append("[").append(record.getLevel()).append("] - ");
        }
        if (recordMessage.endsWith("\\")) {
            builder.append(recordMessage.substring(0, recordMessage.length() - 1));
            this.appendLine = true;
        } else {
            builder.append(recordMessage);
            builder.append("\n");
            this.appendLine = false;
        }
        return builder.toString();
    }
}

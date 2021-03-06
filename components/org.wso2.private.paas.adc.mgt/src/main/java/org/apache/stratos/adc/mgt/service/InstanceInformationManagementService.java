/*
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied.  See the License for the 
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.adc.mgt.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.adc.mgt.utils.PersistenceManager;

public class InstanceInformationManagementService {

	
    private static final Log log = LogFactory.getLog(InstanceInformationManagementService.class);

    /**
    * Everytime an subscription is started up, this operation is invoked
    *            (by the AgentService)
    * @param instanceIp
    * @param tenantId
    * @param clusterDomain
    * @param clusterSubDomain
    * @param cartridge
    * @param state
    *
    */
    public void updateInstanceState(String instanceIp,
                                  int tenantId,
                                  String clusterDomain,
                                  String clusterSubDomain,
                                  String cartridge,
                                  String state) {

      log.info("Message receieved in Instance Info Service.." + instanceIp + tenantId + clusterDomain + clusterSubDomain
               + cartridge + state);

      try {
        PersistenceManager.persistCartridgeInstanceInfo(instanceIp, clusterDomain, clusterSubDomain, cartridge, state);
    } catch (Exception e) {
       log.error("Exception is occurred in updating subscription state. Reason, " + e.getMessage());
    }
    }
}

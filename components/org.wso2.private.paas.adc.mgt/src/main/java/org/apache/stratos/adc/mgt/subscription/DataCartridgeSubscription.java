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

package org.apache.stratos.adc.mgt.subscription;

import org.apache.stratos.adc.mgt.dao.CartridgeSubscriptionInfo;
import org.apache.stratos.adc.mgt.dao.DataCartridge;
import org.apache.stratos.adc.mgt.dto.Policy;
import org.apache.stratos.adc.mgt.exception.*;
import org.apache.stratos.adc.mgt.payload.PayloadArg;
import org.apache.stratos.adc.mgt.repository.Repository;
import org.apache.stratos.adc.mgt.subscriber.Subscriber;
import org.apache.stratos.adc.mgt.utils.ApplicationManagementUtil;
import org.apache.stratos.adc.mgt.utils.CartridgeConstants;
import org.apache.stratos.cloud.controller.util.xsd.CartridgeInfo;

import java.util.Properties;

public class DataCartridgeSubscription extends SingleTenantCartridgeSubscription {

    protected String host;
    protected String username;
    protected String password;

    public DataCartridgeSubscription(CartridgeInfo cartridgeInfo) {

        super(cartridgeInfo);
        this.host = "localhost";
        this.username = CartridgeConstants.MYSQL_DEFAULT_USER;
        this.password = ApplicationManagementUtil.generatePassword();
    }

    @Override
    public void createSubscription(Subscriber subscriber, String alias, Policy autoscalingPolicy, Repository repository)

            throws InvalidCartridgeAliasException,
            DuplicateCartridgeAliasException, ADCException, RepositoryCredentialsRequiredException,
            RepositoryTransportException, UnregisteredCartridgeException, AlreadySubscribedException,
            RepositoryRequiredException, InvalidRepositoryException, PolicyException {

        super.createSubscription(subscriber, alias, autoscalingPolicy, repository);
    }

    public Repository manageRepository (String repoURL, String repoUserName, String repoUserPassword,
                                        boolean privateRepo, String cartridgeAlias, CartridgeInfo cartridgeInfo,
                                        String tenantDomain) {

        //no repository for data cartridge instances
        return null;
    }

    public PayloadArg createPayloadParameters() throws ADCException {

        PayloadArg payloadArg = super.createPayloadParameters();
        payloadArg.setDataCartridgeHost(this.getHost());
        payloadArg.setDataCartridgeAdminUser(username);
        payloadArg.setDataCartridgeAdminPassword(password);

        return payloadArg;
    }

    public CartridgeSubscriptionInfo registerSubscription(Properties payloadProperties)
            throws ADCException, UnregisteredCartridgeException {

        ApplicationManagementUtil.registerService(getType(),
                getClusterDomain(),
                getClusterSubDomain(),
                getPayload().createPayload(),
                getPayload().getPayloadArg().getTenantRange(),
                getHostName(),
                ApplicationManagementUtil.setRegisterServiceProperties(getAutoscalingPolicy(),
                        getSubscriber().getTenantId(), getAlias()));

        getPayload().delete();

        DataCartridge dataCartridge = new DataCartridge();
        dataCartridge.setUserName(username);
        dataCartridge.setPassword(password);
        dataCartridge.setDataCartridgeType(getType());

        return ApplicationManagementUtil.createCartridgeSubscription(getCartridgeInfo(), getAutoscalingPolicy(),
                getType(), getAlias(), getSubscriber().getTenantId(), getSubscriber().getTenantDomain(),
                getRepository(), getHostName(), getClusterDomain(), getClusterSubDomain(),
                getMgtClusterDomain(), getMgtClusterSubDomain(), dataCartridge, "PENDING");
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}

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

package org.apache.stratos.adc.mgt.manager;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.adc.mgt.client.CloudControllerServiceClient;
import org.apache.stratos.adc.mgt.connector.CartridgeSubscriptionConnector;
import org.apache.stratos.adc.mgt.connector.CartridgeSubscriptionConnectorFactory;
import org.apache.stratos.adc.mgt.dao.CartridgeSubscriptionInfo;
import org.apache.stratos.adc.mgt.dto.Policy;
import org.apache.stratos.adc.mgt.dto.SubscriptionInfo;
import org.apache.stratos.adc.mgt.exception.*;
import org.apache.stratos.adc.mgt.payload.Payload;
import org.apache.stratos.adc.mgt.payload.PayloadArg;
import org.apache.stratos.adc.mgt.payload.PayloadFactory;
import org.apache.stratos.adc.mgt.repository.Repository;
import org.apache.stratos.adc.mgt.subscriber.Subscriber;
import org.apache.stratos.adc.mgt.subscription.CartridgeSubscription;
import org.apache.stratos.adc.mgt.subscription.factory.CartridgeSubscriptionFactory;
import org.apache.stratos.adc.mgt.utils.ApplicationManagementUtil;
import org.apache.stratos.adc.mgt.utils.PersistenceManager;
import org.apache.stratos.adc.mgt.utils.PolicyHolder;
import org.apache.stratos.cloud.controller.util.xsd.CartridgeInfo;
import org.wso2.carbon.context.CarbonContext;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Manager class for the purpose of managing CartridgeSubscriptionInfo subscriptions, groupings, etc.
 */
public class CartridgeSubscriptionManager {

    private static Log log = LogFactory.getLog(CartridgeSubscriptionManager.class);

    /**
     * Subscribes to a cartridge
     *
     * @param cartridgeType Cartridge type
     * @param cartridgeAlias Cartridge alias
     * @param autoscalingPolicyName Autoscaling policy name
     * @param tenantDomain Subscriing tenant's domain
     * @param tenantId Subscribing tenant's Id
     * @param tenantAdminUsername Subscribing tenant's admin user name
     * @param repositoryType Type of repository
     * @param repositoryURL Repository URL
     * @param isPrivateRepository If a private or a public repository
     * @param repositoryUsername Repository username
     * @param repositoryPassword Repository password
     *
     * @return Subscribed CartridgeSubscriptionInfo object
     * @throws ADCException
     * @throws InvalidCartridgeAliasException
     * @throws DuplicateCartridgeAliasException
     * @throws PolicyException
     * @throws UnregisteredCartridgeException
     * @throws RepositoryRequiredException
     * @throws RepositoryCredentialsRequiredException
     * @throws RepositoryTransportException
     * @throws AlreadySubscribedException
     * @throws InvalidRepositoryException
     */
    public CartridgeSubscription subscribeToCartridge (String cartridgeType, String cartridgeAlias,
                                                  String autoscalingPolicyName, String tenantDomain, int tenantId,
                                                  String tenantAdminUsername, String repositoryType,
                                                  String repositoryURL, boolean isPrivateRepository,
                                                  String repositoryUsername, String repositoryPassword)

            throws ADCException, InvalidCartridgeAliasException, DuplicateCartridgeAliasException, PolicyException,
            UnregisteredCartridgeException, RepositoryRequiredException, RepositoryCredentialsRequiredException,
            RepositoryTransportException, AlreadySubscribedException, InvalidRepositoryException {

        //validate cartridge alias
        ApplicationManagementUtil.validateCartridgeAlias(cartridgeAlias, cartridgeType);

        Policy autoScalingPolicy;
        if(autoscalingPolicyName != null && !autoscalingPolicyName.isEmpty()) {
            autoScalingPolicy = PolicyHolder.getInstance().getPolicy(autoscalingPolicyName);
        } else {
            autoScalingPolicy = PolicyHolder.getInstance().getDefaultPolicy();
        }

        if(autoScalingPolicy == null) {
            throw new PolicyException("Could not load the auto scaling policy.");
        }

        CartridgeInfo cartridgeInfo;
        try {
            cartridgeInfo = CloudControllerServiceClient.getServiceClient().getCartridgeInfo(cartridgeType);

        } catch (UnregisteredCartridgeException e) {
            String message = cartridgeType
                    + " is not a valid cartridgeSubscription type. Please try again with a valid cartridgeSubscription type.";
            log.error(message);
            throw e;

        } catch (Exception e) {
            String message = "Error getting info for " + cartridgeType;
            log.error(message, e);
            throw new ADCException(message, e);
        }

        Subscriber subscriber = new Subscriber(tenantAdminUsername, tenantId, tenantDomain);

        CartridgeSubscription cartridgeSubscription = CartridgeSubscriptionFactory.getCartridgeSubscriptionInstance(cartridgeInfo);
        Repository repository = cartridgeSubscription.manageRepository(repositoryURL, repositoryUsername,
                repositoryPassword, isPrivateRepository, cartridgeAlias, cartridgeInfo, tenantDomain);

        cartridgeSubscription.createSubscription(subscriber, cartridgeAlias, autoScalingPolicy, repository);

        log.info("Tenant [" + tenantId + "] with username [" + tenantAdminUsername +
                " subscribed to " + "] Cartridge Alias " + cartridgeAlias + ", Cartridge Type: " + cartridgeType +
                ", Repo URL: " + repositoryURL + ", Policy: " + autoscalingPolicyName);

        PayloadArg payloadArg = cartridgeSubscription.createPayloadParameters();
        if (payloadArg != null) {

            Payload payload = PayloadFactory.getPayloadInstance(cartridgeInfo.getProvider(), cartridgeType,
                    "/tmp/" + tenantDomain + "-" + cartridgeAlias + ".zip");
            payload.populatePayload(payloadArg);

            //set the payload to the cartridge subscription
            cartridgeSubscription.setPayload(payload);
        }

        //CartridgeInstanceCache.getCartridgeInstanceCache().
        //        addCartridgeInstance(new CartridgeInstanceCacheKey(tenantId, cartridgeAlias), cartridgeSubscription);

        return cartridgeSubscription;
    }

    /**
     * Connects / groups cartridges
     *
     * @param tenantDomain Tenant's domain
     * @param cartridgeSubscription CartridgeSubscriptionInfo object to which the CartridgeSubscriptionInfo denoted by
     *                          connectingCartridgeAlias will be connected to
     * @param connectingCartridgeAlias Alias of the connecting cartridge
     *
     * @throws ADCException
     * @throws NotSubscribedException
     * @throws AxisFault
     */
    public void connectCartridges (String tenantDomain, CartridgeSubscription cartridgeSubscription,
                                   String connectingCartridgeAlias)
            throws ADCException, NotSubscribedException, AxisFault {

        //TODO: retrieve from the cache and connect. For now, new objects are created

        CartridgeSubscription connectingCartridgeSubscription = getCartridgeSubscription(tenantDomain, connectingCartridgeAlias);

        if(cartridgeSubscription == null) {
            String errorMsg = "No cartridge subscription found in cache for tenant " + tenantDomain + ", alias " +
                    cartridgeSubscription.getAlias() + ",  connecting aborted";
            log.error(errorMsg);
            return;
        }

        if(connectingCartridgeSubscription == null) {
            String errorMsg = "No cartridge subscription found in cache for tenant " + tenantDomain + ", alias " +
                    connectingCartridgeAlias + ",  connecting aborted";
            log.error(errorMsg);
            return;
        }

        CartridgeSubscriptionConnector cartridgeSubscriptionConnector = CartridgeSubscriptionConnectorFactory.
                getCartridgeInstanceConnector(connectingCartridgeSubscription.getType());

        cartridgeSubscription.connect(connectingCartridgeAlias);

        //PayloadArg payloadArg = cartridgeSubscription.createPayloadParameters();

        //get additional payload params for connecting cartridges
        Properties payloadProperties = cartridgeSubscriptionConnector.createConnection(cartridgeSubscription,
                connectingCartridgeSubscription);
        StringBuilder connectionParamsBuilder = new StringBuilder();
        Set<Map.Entry<Object,Object>> payloadParamEntries = payloadProperties.entrySet();

        for (Map.Entry<Object, Object> payloadParamEntry : payloadParamEntries) {
            connectionParamsBuilder.append(",");
            connectionParamsBuilder.append(payloadParamEntry.getKey().toString());
            connectionParamsBuilder.append("=");
            connectionParamsBuilder.append(payloadParamEntry.getValue().toString());
        }

        //add additional connection relates parameters to the payload
        cartridgeSubscription.getPayload().populatePayload(connectionParamsBuilder.toString());

        /*
        payloadArg.setUserDefinedPayload(connectionParamsBuilder.toString());
        Payload payload = PayloadFactory.getPayloadInstance(cartridgeSubscription.getCartridgeInfo().getProvider(),
                cartridgeSubscription.getType(),
                "/tmp/" + tenantDomain + "-" + cartridgeSubscription.getAlias() + ".zip");
        payload.populatePayload(payloadArg);
        payload.createPayload();
        */
    }

    /**
     * Registers the cartridge subscription for the given CartridgeSubscriptionInfo object
     *
     * @param cartridgeSubscription CartridgeSubscription subscription
     *
     * @return SubscriptionInfo object populated with relevant information
     * @throws ADCException
     * @throws UnregisteredCartridgeException
     */
    public SubscriptionInfo registerCartridgeSubscription(CartridgeSubscription cartridgeSubscription)
            throws ADCException, UnregisteredCartridgeException {

        /*CartridgeSubscriptionInfo cartridgeSubscription = CartridgeInstanceCache.getCartridgeInstanceCache().
                getCartridgeSubscription(new CartridgeInstanceCacheKey(tenantId, alias));

        if(cartridgeSubscription == null) {
            throw new ADCException("Unable to find cartridge with alias " + alias + ", for tenant Id " + tenantId +
                    " in cache");
        }*/

        CartridgeSubscriptionInfo cartridgeSubscriptionInfo = cartridgeSubscription.registerSubscription(null);

        int subscriptionId;
        try {
            subscriptionId = PersistenceManager.persistSubscription(cartridgeSubscriptionInfo);

        } catch (Exception e) {
            String errorMsg = "Error saving subscription for tenant " +
                    cartridgeSubscription.getSubscriber().getTenantDomain() + ", alias " + cartridgeSubscription.getType();
            log.error(errorMsg);
            throw new ADCException(errorMsg, e);
        }

        cartridgeSubscription.setSubscriptionId(subscriptionId);
        ApplicationManagementUtil.addDNSEntry(cartridgeSubscriptionInfo.getAlias(), cartridgeSubscription.getType());

        return ApplicationManagementUtil.
                createSubscriptionResponse(cartridgeSubscriptionInfo, cartridgeSubscription.getRepository());
    }

    /**
     * Unsubscribe from a Cartridge
     *
     * @param tenantDomain Tenant's domain
     * @param alias Alias given at subscription time
     * @throws ADCException
     * @throws NotSubscribedException
     */
    public void unsubscribeFromCartridge (String tenantDomain, String alias)
            throws ADCException, NotSubscribedException {

        //TODO: retrieve from the cache and connect. For now, new objects are created

        CartridgeSubscription cartridgeSubscription = getCartridgeSubscription(tenantDomain, alias);

        if(cartridgeSubscription != null) {
            cartridgeSubscription.removeSubscription();
            //CartridgeInstanceCache.getCartridgeInstanceCache().removeCartridgeInstance(cartridgeInstanceCacheKey);
        }
        else {
            if(log.isDebugEnabled()) {
                log.debug("No cartridge subscription found with alias " + alias + " for tenant " + tenantDomain);
            }
        }
    }

    /*public List<CartridgeSubscriptionInfo> getCartridgeInstances (int tenantId) throws ADCException, NotSubscribedException {

        List<CartridgeSubscriptionInfo> cartridgeSubscriptions = getCartridgeSubscriptions(tenantId);
        List<CartridgeSubscriptionInfo> cartridgeInstances = new ArrayList<CartridgeSubscriptionInfo>();
        CartridgeInfo cartridgeInfo;

        for(CartridgeSubscriptionInfo cartridgeSubscription : cartridgeSubscriptions) {
            try {
                cartridgeInfo = CloudControllerServiceClient.getServiceClient().
                        getCartridgeInfo(cartridgeSubscription.getCartridge());
                cartridgeInstances.add(populateCartridgeSubscriptionInformation(cartridgeInfo, cartridgeSubscription));

            } catch (Exception e) {
                throw new ADCException(e.getMessage(), e);
            }
        }

        return cartridgeInstances;
    }*/

    /**
     * Creates and returns a CartridgeSubscription object
     *
     * @param tenantDomain Tenant's domain
     * @param alias alias given at subscription
     *
     * @return CartridgeSubscription object populated with relevant information
     * @throws ADCException
     * @throws NotSubscribedException
     */
    public CartridgeSubscription getCartridgeSubscription(String tenantDomain, String alias)
            throws ADCException, NotSubscribedException {

        CartridgeSubscriptionInfo cartridgeSubscriptionInfo = getCartridgeSubscriptionInfo(tenantDomain, alias);

        CartridgeInfo cartridgeInfo;
        try {
            cartridgeInfo = CloudControllerServiceClient.getServiceClient().
                    getCartridgeInfo(cartridgeSubscriptionInfo.getCartridge());
        } catch (Exception e) {
            throw new ADCException(e.getMessage(), e);
        }

        return populateCartridgeSubscriptionInformation(cartridgeInfo, cartridgeSubscriptionInfo);
    }

    private CartridgeSubscriptionInfo getCartridgeSubscriptionInfo(String tenantDomain, String alias)
            throws ADCException, NotSubscribedException {

        CartridgeSubscriptionInfo subscription;
        try {
            subscription = PersistenceManager.getSubscription(tenantDomain, alias);

        } catch (Exception e) {
            String msg = "Failed to get subscriptions for " + tenantDomain;
            log.error(msg, e);
            throw new ADCException(msg, e);
        }

        if (subscription == null) {
            String msg = "Tenant " + tenantDomain + " has not subscribed for cartridges";
            log.error(msg);
            throw new NotSubscribedException(msg, msg);
        }

        return subscription;

    }

    private List<CartridgeSubscriptionInfo> getCartridgeSubscriptions (int tenantId) throws ADCException, NotSubscribedException {

        List<CartridgeSubscriptionInfo> subscriptions;
        try {
            subscriptions = PersistenceManager.getSubscriptionsForTenant(tenantId);

        } catch (Exception e) {
            String msg = "Failed to get subscriptions for " + tenantId;
            log.error(msg, e);
            throw new ADCException(msg, e);
        }

        if (subscriptions == null) {
            String msg = "Tenant " + tenantId + " has not subscribed for cartridges";
            log.error(msg);
            throw new NotSubscribedException(msg, msg);
        }

        return subscriptions;
    }

    private CartridgeSubscription populateCartridgeSubscriptionInformation(CartridgeInfo cartridgeInfo,
                                                                           CartridgeSubscriptionInfo cartridgeSubscriptionInfo)
            throws ADCException {

        CartridgeSubscription cartridgeSubscription = CartridgeSubscriptionFactory.getCartridgeSubscriptionInstance(cartridgeInfo);

        cartridgeSubscription.setSubscriptionId(cartridgeSubscriptionInfo.getSubscriptionId());
        cartridgeSubscription.setAlias(cartridgeSubscriptionInfo.getAlias());
        cartridgeSubscription.setHostName(cartridgeSubscriptionInfo.getHostName());
        cartridgeSubscription.setClusterDomain(cartridgeSubscriptionInfo.getClusterDomain());
        cartridgeSubscription.setClusterSubDomain(cartridgeSubscriptionInfo.getClusterSubdomain());
        cartridgeSubscription.setMgtClusterDomain(cartridgeSubscriptionInfo.getMgtClusterDomain());
        cartridgeSubscription.setMgtClusterSubDomain(cartridgeSubscriptionInfo.getMgtClusterSubDomain());
        Policy autoScalingPolicy;
        if(cartridgeSubscriptionInfo.getPolicy() != null && !cartridgeSubscriptionInfo.getPolicy().isEmpty()) {
            autoScalingPolicy = PolicyHolder.getInstance().getPolicy(cartridgeSubscriptionInfo.getPolicy());
        } else {
            autoScalingPolicy = PolicyHolder.getInstance().getDefaultPolicy();
        }
        cartridgeSubscription.setAutoscalingPolicy(autoScalingPolicy);
        Subscriber subscriber = new Subscriber(CarbonContext.getThreadLocalCarbonContext().getUsername(),
                cartridgeSubscriptionInfo.getTenantId(), cartridgeSubscriptionInfo.getTenantDomain());
        cartridgeSubscription.setSubscriber(subscriber);
        cartridgeSubscription.setRepository(cartridgeSubscriptionInfo.getRepository());

        return cartridgeSubscription;
    }
}

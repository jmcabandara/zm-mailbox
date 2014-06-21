/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2009, 2010, 2011, 2013 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;

import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.DynamicGroup;
import com.zimbra.cs.account.Group;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.common.account.Key.DistributionListBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class RemoveDistributionListAlias extends DistributionListDocumentHandler {

    /**
     * must be careful and only allow access to domain if domain admin
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }
    
    @Override
    protected Group getGroup(Element request) throws ServiceException {
        String id = request.getAttribute(AdminConstants.E_ID, null);
        if (id != null) {
            return Provisioning.getInstance().getGroup(DistributionListBy.id, id);
        } else {
            return null;
        }
    }

	public Element handle(Element request, Map<String, Object> context) 
	throws ServiceException {

        ZimbraSoapContext zsc = getZimbraSoapContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String alias = request.getAttribute(AdminConstants.E_ALIAS);

	    Group group = getGroupFromContext(context);
	    
        String dlName = "";
        if (group != null) {
            if (group.isDynamic()) {
                checkDynamicGroupRight(zsc, (DynamicGroup) group, Admin.R_removeGroupAlias);
            } else {
                checkDistributionListRight(zsc, (DistributionList) group, Admin.R_removeDistributionListAlias);
            }
            dlName = group.getName();
        }
        
        // if the admin can remove an alias in the domain
        checkDomainRightByEmail(zsc, alias, Admin.R_deleteAlias);
        
        // even if dl is null, we still invoke removeAlias and throw an exception afterwards.
        // this is so dangling aliases can be cleaned up as much as possible
        prov.removeGroupAlias(group, alias);
        
        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "RemoveDistributionListAlias", "name", dlName, "alias", alias})); 
        
        if (group == null) {
            String id = request.getAttribute(AdminConstants.E_ID, null);
            throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(id);
        }
        
	    Element response = zsc.createElement(AdminConstants.REMOVE_DISTRIBUTION_LIST_ALIAS_RESPONSE);
	    return response;
	}
	
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_removeDistributionListAlias);
        relatedRights.add(Admin.R_removeGroupAlias);
        relatedRights.add(Admin.R_deleteAlias);
    }
}
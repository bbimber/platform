package org.labkey.study.requirements;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.Collection;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:37:53 PM
 */
public interface RequirementProvider<R extends Requirement<R>, 
                                     A extends RequirementActor<A>>
{
    R getRequirement(Container container, Object requirementPrimaryKey);

    A[] getActors(Container c);

    A getActor(Container c, Object primaryKey);

    Collection<A> getActorsInUse(Container c);
    
    R[] getDefaultRequirements(Container container, RequirementType type);

    void generateDefaultRequirements(User user, RequirementOwner owner);

    void purgeContainer(Container c);

    R createDefaultRequirement(User user, R requirement, RequirementType type);

    RequirementType[] getRequirementTypes();

    R createRequirement(User user, RequirementOwner owner, R requirement);

    R createRequirement(User user, RequirementOwner owner, R requirement, boolean forceDuplicate);

    void deleteRequirements(RequirementOwner owner);

    R[] getRequirements(RequirementOwner owner);
}

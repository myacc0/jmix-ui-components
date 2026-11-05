package uz.kapitalbank.umida.view.report.access;

import com.google.common.base.Strings;
import io.jmix.core.EntityStates;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

/**
 * The caption of a subdivision in the pickers of the reports' access: its name with the name of its parent,
 * since the names of the subdivisions repeat across the bank. The parent is shown when loaded.
 */
@Component
public class SubdivisionCaptions {

    private final EntityStates entityStates;

    public SubdivisionCaptions(EntityStates entityStates) {
        this.entityStates = entityStates;
    }

    public String caption(OrgStructureSubdivision subdivision) {
        String name = Strings.nullToEmpty(subdivision.getName());
        OrgStructureSubdivision parent = entityStates.isLoaded(subdivision, "parent") ? subdivision.getParent() : null;
        return parent != null && entityStates.isLoaded(parent, "name") ? name + " (" + parent.getName() + ")" : name;
    }
}

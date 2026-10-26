package uz.kapitalbank.umida.dto.dq;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import uz.kapitalbank.umida.entity.dq.DqIssue;

import java.util.UUID;

/**
 * Non-persistent row of the grouped issue list: either a group — the value the issues are grouped by,
 * with the number of its issues — or an issue under its group. An issue row has the id of its issue,
 * a group row an id derived from the grouping and the value, so that both stay the same across reloads.
 */
@JmixEntity(name = "umida_DqIssueTreeItem", annotatedPropertiesOnly = true)
public class DqIssueTreeItem {

    @JmixId
    @JmixProperty(mandatory = true)
    private UUID id;

    /** The group of an issue row; none for a group row. */
    @JmixProperty
    private DqIssueTreeItem parent;

    /** The group caption with the number of its issues, or the code of the issue. */
    @InstanceName
    @JmixProperty
    private String caption;

    /** The issues of a group row; none for an issue row. */
    @JmixProperty
    private Integer count;

    /** The issue of an issue row; none for a group row. */
    @JmixProperty
    private DqIssue issue;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DqIssueTreeItem getParent() {
        return parent;
    }

    public void setParent(DqIssueTreeItem parent) {
        this.parent = parent;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public DqIssue getIssue() {
        return issue;
    }

    public void setIssue(DqIssue issue) {
        this.issue = issue;
    }

    public boolean isGroup() {
        return issue == null;
    }
}

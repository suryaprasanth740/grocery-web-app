package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An admin closing a report: what was found, and optionally an action against the shop or product. */
public class ResolveReportRequest {

    public enum Action { NONE, HIDE_PRODUCT, SUSPEND_SHOP }

    @NotNull(message = "Choose RESOLVED or DISMISSED")
    private ReportStatus status;

    @NotBlank(message = "Write what you found (kept in the activity log)")
    @Size(min = 5, max = 300, message = "Note must be 5 to 300 characters")
    private String note;

    private Action action = Action.NONE;

    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action == null ? Action.NONE : action; }
}

// JobHub - "All notifications" page (Story #184 / Ticket #195).
//
// A dedicated, paginated view of every notification (read AND unread), newest-first
// as returned by the API. Reached from the sidebar's "Notifications" nav item (story
// #206 removed the redundant sidebar-footer bell that used to offer a second path).
//
// Rows render BOTH read and unread variants. A row click marks it read (if unread) and
// navigates to its application (if it carries an applicationId); mark-read and
// navigation are independent - a mark-read failure still lets navigation proceed, and
// an unread row flips to read IN PLACE (it is never removed by a row click).
//
// Story #206 / Ticket #234 adds two actions on top of that:
//  - "Mark all as read" (PATCH /notifications/read-all): flips every rendered unread
//    row to read in place, no re-fetch, disabled when nothing rendered is unread.
//  - Per-row delete (DELETE /notifications/{id}), confirmed inline (mirrors the
//    Applications screen's delete-confirm pattern), with independent per-row confirm
//    state so confirming/cancelling one row never affects another.
import React from "react";
import Icon from "../components/Icon.jsx";
import * as UI from "../components/ui.jsx";
import { NotificationIdentity } from "../components/NotificationIdentity.jsx";
import { listNotifications, markNotificationRead, markAllNotificationsRead, deleteNotification } from "../api/notifications.js";
import { iconForType, timeAgo, categoryOf } from "../components/notificationPresentation.js";

const { Topbar, Empty, Button } = UI;

const PAGE_SIZE = 20;

// Story #439 / Ticket #535 (ADR 0031, BR-439-4/6/7): the application identity row
// renders ONLY on a positive APPLICATION category signal, never on the absence of
// one. JOB_POST gets its own recognised branch (currently inert/reserved, per
// BR-439-7) rather than being folded into a generic "not APPLICATION" catch-all,
// so a future job-post story is additive here instead of another breaking change.
// ACCOUNT (and any unrecognised/missing category, already normalised to ACCOUNT by
// categoryOf) renders no identity row and no substitute header/badge (BR-439-6).
function NotificationRowIdentity({ notification }) {
  switch (categoryOf(notification)) {
    case "APPLICATION":
      return <NotificationIdentity notification={notification} />;
    case "JOB_POST":
      // Reserved: taxonomy-only in story #439, no NotificationType maps to it yet.
      // Renders identically to ACCOUNT (no identity row) until a future story
      // populates job-post-scoped chrome.
      return null;
    case "ACCOUNT":
    default:
      return null;
  }
}

function NotificationRow({ notification, onClick, confirmingDelete, onDeleteClick, onDeleteCancel, onDeleteConfirm }) {
  const testId = notification.read ? "notification-row-read" : "notification-row-unread";
  return (
    <div
      className={"notification-row " + (notification.read ? "read" : "unread")}
      data-testid={testId}
      onClick={() => onClick(notification)}
    >
      <span className="notification-row-icon" data-testid={`notification-icon-${notification.type}`}>
        <Icon name={iconForType(notification.type)} size={16} />
      </span>
      <div className="notification-row-body">
        <NotificationRowIdentity notification={notification} />
        <div className="notification-row-title">{notification.title}</div>
        <div className="notification-row-message">{notification.message}</div>
        <div className="notification-row-time">{timeAgo(notification.createdAt)}</div>
      </div>
      {!notification.read && <span className="notification-row-dot" aria-hidden="true" />}
      <div className="notification-row-actions" onClick={(e) => e.stopPropagation()}>
        {!confirmingDelete ? (
          <Button
            variant="ghost"
            size="sm"
            icon="trash"
            data-testid="notification-row-delete"
            aria-label="Delete notification"
            onClick={(e) => { e.stopPropagation(); onDeleteClick(notification.id); }}
          />
        ) : (
          <div className="notification-row-delete-confirm" data-testid="notification-row-delete-confirm">
            <Button
              variant="ghost"
              size="sm"
              data-testid="notification-row-delete-cancel"
              onClick={(e) => { e.stopPropagation(); onDeleteCancel(); }}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              size="sm"
              icon="trash"
              data-testid="notification-row-delete-confirm-button"
              onClick={(e) => { e.stopPropagation(); onDeleteConfirm(notification.id); }}
            >
              Delete
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

function NotificationsPager({ page, totalPages, onPage }) {
  if (totalPages <= 1) return null;
  return (
    <div className="notifications-page-pager" data-testid="notifications-page-pager">
      <button
        type="button"
        data-testid="notifications-page-pager-prev"
        aria-label="Previous page"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
      >
        <Icon name="chevron-left" size={14} />
      </button>
      <span data-testid="notifications-page-pager-indicator">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        data-testid="notifications-page-pager-next"
        aria-label="Next page"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
      >
        <Icon name="chevron-right" size={14} />
      </button>
    </div>
  );
}

function NotificationsScreen({ goto, openSearch, onOpenApplication, onLogout, onAllRead, onUnreadDeleted, onUnreadRead, initialPage = 0 }) {
  const [page, setPage] = React.useState(initialPage);
  const [totalPages, setTotalPages] = React.useState(1);
  const [listState, setListState] = React.useState("loading"); // loading | error | ready
  const [notifications, setNotifications] = React.useState([]);
  const [actionError, setActionError] = React.useState(false);
  // Per-row delete-confirm state (story #206 / AC-3): a Set of notification ids whose
  // inline confirm step is currently open. Deliberately a Set, not a single scalar, so
  // opening one row's confirm never affects another row's confirm state (TC-206-F-25).
  const [confirmDeleteIds, setConfirmDeleteIds] = React.useState(() => new Set());

  const handleAuthError = React.useCallback((err) => {
    if (err && err.status === 401) {
      onLogout?.();
      return true;
    }
    return false;
  }, [onLogout]);

  const load = React.useCallback(async (p) => {
    setListState("loading");
    setActionError(false);
    try {
      const res = await listNotifications({ page: p, size: PAGE_SIZE, readStatus: "all" });
      setNotifications((res && res.content) || []);
      setTotalPages((res && res.totalPages) || 1);
      setListState("ready");
    } catch (err) {
      if (handleAuthError(err)) return;
      setListState("error");
    }
  }, [handleAuthError]);

  React.useEffect(() => {
    load(page);
  }, [page, load]);

  function handlePage(next) {
    if (next < 0 || next >= totalPages) return;
    setPage(next);
  }

  // Mark-read and navigation are independent actions, mirroring the bell (story #182/#183):
  // a row with an applicationId always navigates regardless of read state or mark-read
  // outcome; a row flips read->in-place on a successful mark-read (never removed, unlike
  // the bell's unread-only popup); a mark-read failure surfaces an action-error but still
  // lets navigation proceed.
  // Ticket #237 / AC-C12: a successful mark-read on a previously-unread row also tells the
  // App shell to decrement the top-nav badge right away, rather than waiting for the next
  // ~60s poll tick. Only fires on success, only for rows that were actually unread.
  async function handleRowClick(n) {
    const clickable = n.applicationId != null;

    if (!n.read) {
      try {
        await markNotificationRead(n.id);
        setActionError(false);
        setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
        onUnreadRead?.();
      } catch (err) {
        if (handleAuthError(err)) return;
        setActionError(true);
      }
    }

    if (clickable) {
      onOpenApplication?.(n.applicationId);
    }
  }

  // AC-2: marks every notification read in one call, then flips the rendered rows
  // in place (no removal, no re-fetch). A failure leaves every row unchanged and
  // surfaces the action-error; a 401 logs the user out instead.
  // Ticket #237 / AC-C5: on success, also tells the App shell to zero the top-nav
  // badge immediately, rather than waiting for the next ~60s poll tick.
  async function handleMarkAllRead() {
    try {
      await markAllNotificationsRead();
      setActionError(false);
      setNotifications((prev) => prev.map((x) => ({ ...x, read: true })));
      onAllRead?.();
    } catch (err) {
      if (handleAuthError(err)) return;
      setActionError(true);
    }
  }

  // AC-3: opens/cancels the inline per-row delete confirm. Per-row Set state keeps
  // each row's confirm step independent (TC-206-F-25).
  function handleDeleteClick(id) {
    setConfirmDeleteIds((prev) => {
      const next = new Set(prev);
      next.add(id);
      return next;
    });
  }

  function handleDeleteCancel(id) {
    setConfirmDeleteIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }

  // Ticket #237 / AC-C6: deleting an UNREAD row also decrements the top-nav badge
  // right away; deleting an already-read row leaves the badge untouched.
  async function handleDeleteConfirm(id) {
    const target = notifications.find((x) => x.id === id);
    try {
      await deleteNotification(id);
      setActionError(false);
      setNotifications((prev) => prev.filter((x) => x.id !== id));
      setConfirmDeleteIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
      if (target && !target.read) onUnreadDeleted?.();
    } catch (err) {
      if (handleAuthError(err)) return;
      setActionError(true);
    }
  }

  const hasUnread = notifications.some((n) => !n.read);

  return (
    <>
      <Topbar title="Notifications" sub="All your notifications, read and unread" searchLabel="Search…" onSearchClick={openSearch} />
      <div className="content" data-testid="notifications-page">
        {actionError && (
          <div className="notifications-action-error" data-testid="notifications-action-error" role="alert">
            Couldn't update that notification, please try again.
          </div>
        )}

        {listState === "loading" && (
          <div className="notifications-loading" data-testid="notifications-loading">
            Loading…
          </div>
        )}

        {listState === "error" && (
          <div className="notifications-list-error" data-testid="notifications-list-error" role="alert">
            Couldn't load notifications, please try again later.
          </div>
        )}

        {listState === "ready" && notifications.length === 0 && (
          <div data-testid="notifications-empty">
            <Empty
              icon="bell"
              title="You're all caught up, no notifications yet."
            />
          </div>
        )}

        {listState === "ready" && notifications.length > 0 && (
          <>
            <div className="notifications-list-actions">
              <Button
                variant="secondary"
                size="sm"
                data-testid="notifications-mark-all-read"
                disabled={!hasUnread}
                onClick={handleMarkAllRead}
              >
                Mark all as read
              </Button>
            </div>
            <div className="notification-list" data-testid="notifications-list">
              {notifications.map((n) => (
                <NotificationRow
                  key={n.id}
                  notification={n}
                  onClick={handleRowClick}
                  confirmingDelete={confirmDeleteIds.has(n.id)}
                  onDeleteClick={handleDeleteClick}
                  onDeleteCancel={() => handleDeleteCancel(n.id)}
                  onDeleteConfirm={handleDeleteConfirm}
                />
              ))}
            </div>
            <NotificationsPager page={page} totalPages={totalPages} onPage={handlePage} />
          </>
        )}
      </div>
    </>
  );
}

export { NotificationsScreen };
export default NotificationsScreen;

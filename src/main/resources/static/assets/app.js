(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);
    const pageSize = 8;
    const preview = location.protocol === "file:" || new URLSearchParams(location.search).get("preview") === "1";
    const roles = {
        ADMIN: "Администратор",
        TEACHER: "Преподаватель",
        SUPER_ADMIN: "Суперадминистратор",
    };
    const collator = new Intl.Collator("ru", { sensitivity: "base", numeric: true });
    const number = new Intl.NumberFormat("ru");
    const sessionKey = "dean-office.session";
    const state = {
        section: "students",
        session: null,
        page: 0,
        total: 0,
        request: 0,
        dialog: 0,
        userList: null,
    };
    let searchTimer;
    let expiryTimer;
    let toastTimer;
    let dialogOpener;

    function stored(storage, key) {
        try { return window[storage].getItem(key); } catch { return null; }
    }

    function persist(storage, key, value) {
        try {
            if (value === null) window[storage].removeItem(key);
            else window[storage].setItem(key, value);
        } catch {
            // Browser privacy settings can disable storage.
        }
    }

    function setTheme(theme) {
        const dark = theme === "dark";
        document.documentElement.dataset.theme = dark ? "dark" : "light";
        $("theme").setAttribute("aria-pressed", String(dark));
        $("theme-label").textContent = dark ? "Светлая тема" : "Тёмная тема";
    }

    function element(tag, text, className) {
        const item = document.createElement(tag);
        if (text !== undefined) item.textContent = text;
        if (className) item.className = className;
        return item;
    }

    function button(text, kind, listener) {
        const item = element("button", text, `button ${kind}`);
        item.type = "button";
        item.addEventListener("click", listener);
        return item;
    }

    function roleNames(values) {
        return values.map((role) => String(role).replace(/^ROLE_/, ""));
    }

    function allow(section) {
        const values = state.session?.roles ?? [];
        return section === "users" ? values.includes("SUPER_ADMIN") : values.includes("ADMIN") || values.includes("TEACHER");
    }

    function mayWrite() {
        return state.section === "users" ? allow("users") : state.session?.roles.includes("ADMIN");
    }

    function formError(form, message = "") {
        const error = form.querySelector(".form-error");
        error.textContent = message;
        error.hidden = !message;
    }

    function notify(message) {
        clearTimeout(toastTimer);
        $("announcement").textContent = message;
        $("toast").textContent = message;
        $("toast").hidden = false;
        toastTimer = setTimeout(() => { $("toast").hidden = true; }, 3600);
    }

    function signOut(message = "", focus = true) {
        clearTimeout(expiryTimer);
        clearTimeout(searchTimer);
        clearTimeout(toastTimer);
        $("toast").hidden = true;
        persist("sessionStorage", sessionKey, null);
        state.session = null;
        state.userList = null;
        state.request++;
        if ($("dialog").open) $("dialog").close();
        $("workspace").hidden = true;
        $("login").hidden = false;
        $("login-form").reset();
        formError($("login-form"), message);
        if (focus) $("login-email").focus();
    }

    function begin(session) {
        state.session = session;
        state.userList = null;
        clearTimeout(expiryTimer);
        if (!preview) {
            persist("sessionStorage", sessionKey, JSON.stringify(session));
            expiryTimer = setTimeout(() => signOut("Сеанс завершён. Войдите снова."), Math.max(0, session.expiresAt - Date.now()));
        }
        $("account-email").textContent = session.email;
        $("account-roles").textContent = session.roles.map((role) => roles[role] ?? role).join(", ");
        $("students-nav").hidden = !allow("students");
        $("users-nav").hidden = !allow("users");
        $("login").hidden = true;
        $("workspace").hidden = false;
        selectSection(allow("students") ? "students" : "users");
    }

    function demoRole(role) {
        $("dialog").close();
        begin({ token: "preview", email: "demo@example.test", roles: [role] });
    }

    async function api(path, { method = "GET", body, signed = true } = {}) {
        const session = state.session;
        if (preview) return window.previewApi(path, method, body, session.roles);

        const headers = { Accept: "application/json" };
        if (signed && session) headers.Authorization = `Bearer ${session.token}`;
        if (body !== undefined) headers["Content-Type"] = "application/json";
        let response;
        try {
            response = await fetch(new URL(`./api/v1/${path}`, location.href), {
                method,
                headers,
                body: body === undefined ? undefined : JSON.stringify(body),
            });
        } catch {
            throw new Error("Сервер недоступен. Проверьте подключение и попробуйте снова.");
        }
        const data = response.headers.get("content-type")?.includes("json") ? await response.json().catch(() => null) : null;
        if (response.ok) {
            if (data === null && method !== "DELETE") throw new Error("Сервер вернул неожиданный ответ.");
            return data;
        }
        if (response.status === 401 && signed && state.session === session) {
            signOut("Сеанс завершён. Войдите снова.");
        }
        const fallback = {
            400: "Проверьте введённые данные.",
            401: "Неверная электронная почта или пароль.",
            403: "У вашей учётной записи нет доступа к этой операции.",
            404: "Запись не найдена.",
            409: "Запись конфликтует с существующими данными.",
        };
        const message = response.status < 500 && data && typeof data.message === "string" ? data.message : null;
        throw new Error(message || fallback[response.status] || "Не удалось выполнить запрос. Попробуйте снова.");
    }

    function selectSection(section) {
        clearTimeout(searchTimer);
        state.section = section;
        state.page = 0;
        state.total = 0;
        $("table-wrap").hidden = true;
        for (const item of document.querySelectorAll(".nav-button")) {
            if (item.dataset.section === section) item.setAttribute("aria-current", "page");
            else item.removeAttribute("aria-current");
        }
        const users = section === "users";
        $("directory-title").textContent = users ? "Пользователи" : "Студенты";
        $("directory-description").textContent = users ? "Учётные записи сотрудников и их роли" : "Имена и контактные данные студентов";
        $("add-label").textContent = users ? "Добавить пользователя" : "Добавить студента";
        $("add-entry").hidden = !mayWrite();
        $("filter-field").hidden = !users;
        $("role-filter").value = "";
        $("query").value = "";
        $("query").maxLength = users ? 254 : 200;
        $("query").placeholder = users ? "Электронная почта" : "Имя, фамилия или ID";
        $("search-label").textContent = users ? "Поиск по электронной почте" : "Поиск по имени, фамилии или ID";
        $("table-caption").textContent = users ? "Список пользователей" : "Список студентов";
        const fields = users ? [["email", "Электронная почта"]] : [["surname", "Фамилия"], ["name", "Имя"]];
        $("sort").replaceChildren(...[
            ["id:asc", "ID по возрастанию"], ["id:desc", "ID по убыванию"],
            ...fields.flatMap(([key, label]) => [[`${key}:asc`, `${label} ↑`], [`${key}:desc`, `${label} ↓`]]),
        ].map(([value, label]) => new Option(label, value)));
        const headings = users ? ["ID", "Электронная почта", "Роли"] : ["ID", "Студент"];
        const actionHeading = element("span", "Действия", "visually-hidden");
        $("columns").replaceChildren(...headings.map((name) => element("th", name)), element("th"));
        $("columns").lastElementChild.append(actionHeading);
        for (const heading of $("columns").children) heading.scope = "col";
        if (allow(section)) void loadRows();
        else listState("Для вашей учётной записи нет доступных разделов.");
    }

    function listState(message, retry = false) {
        $("table-wrap").hidden = true;
        $("list-status").hidden = false;
        $("list-message").textContent = message;
        $("retry").hidden = !retry;
        $("list-footer").hidden = true;
    }

    async function loadRows() {
        const request = ++state.request;
        const users = state.section === "users";
        const query = $("query").value.trim();
        const [sort, direction] = $("sort").value.split(":");
        const page = state.page;
        $("directory-card").setAttribute("aria-busy", "true");
        if ($("table-wrap").hidden) listState("Загрузка списка…");
        $("previous").disabled = true;
        $("next").disabled = true;
        try {
            let records;
            let total;
            if (users) {
                const accounts = state.userList ?? (await api("users")).responses;
                if (request !== state.request) return;
                state.userList = accounts;
                const filter = $("role-filter").value;
                const term = query.toLowerCase();
                const filtered = accounts.filter((user) => user.email.toLowerCase().includes(term) && (!filter || user.roles.includes(filter)));
                filtered.sort((left, right) => {
                    const order = sort === "id" ? left.id - right.id : collator.compare(left.email, right.email);
                    return order * (direction === "desc" ? -1 : 1) || left.id - right.id;
                });
                total = filtered.length;
                records = filtered.slice(page * pageSize, (page + 1) * pageSize);
            } else {
                const parameters = new URLSearchParams({ query, sort, direction, page: String(page), size: String(pageSize) });
                const result = await api(`student?${parameters}`);
                records = result.response;
                total = result.totalCount;
            }
            if (request !== state.request) return;
            const lastPage = Math.max(0, Math.ceil(total / pageSize) - 1);
            if (page > lastPage) {
                state.page = lastPage;
                void loadRows();
                return;
            }
            renderRows(records, total, query, users);
        } catch (error) {
            if (request === state.request) listState(error.message, true);
        } finally {
            if (request === state.request) $("directory-card").removeAttribute("aria-busy");
        }
    }

    function renderRows(records, total, query, users) {
        state.total = total;
        const focusedRecord = document.activeElement?.dataset.recordId;
        if (!records.length) {
            listState(query || (users && $("role-filter").value) ? "По заданным условиям ничего не найдено." : "Записей пока нет.");
            if (focusedRecord) $("query").focus();
            return;
        }
        $("rows").replaceChildren(...records.map((record) => makeRow(record, users)));
        $("list-status").hidden = true;
        $("table-wrap").hidden = false;
        $("list-footer").hidden = false;
        const offset = state.page * pageSize;
        const pages = Math.ceil(total / pageSize);
        $("page-summary").textContent = `${number.format(offset + 1)}–${number.format(offset + records.length)} из ${number.format(total)}`;
        $("pagination").hidden = pages < 2;
        $("page-number").textContent = `${number.format(state.page + 1)} / ${number.format(pages)}`;
        $("previous").disabled = state.page === 0;
        $("next").disabled = state.page >= pages - 1;
        if (focusedRecord) {
            const replacement = [...$("rows").querySelectorAll(".record-button")].find((item) => item.dataset.recordId === focusedRecord);
            (replacement ?? $("query")).focus();
        }
    }

    function makeRow(record, users) {
        const title = users ? record.email : `${record.surname ?? ""} ${record.name ?? ""}`.trim() || "Без имени";
        const row = element("tr");
        row.append(element("td", String(record.id), "record-id"), element("td", title, "record-name"));
        if (users) {
            const badges = element("div", undefined, "role-badges");
            for (const role of record.roles) badges.append(element("span", roles[role] ?? role, "role-badge"));
            const roleCell = element("td");
            roleCell.append(badges);
            row.append(roleCell);
        }
        const actions = element("td", undefined, "actions");
        const open = element("button", users ? "Изменить" : "Открыть", "record-button");
        open.type = "button";
        open.dataset.recordId = String(record.id);
        open.setAttribute("aria-label", `${open.textContent} — ${title}`);
        open.addEventListener("click", () => {
            if (users) showEditor("users", record);
            else void openStudent(record.id);
        });
        actions.append(open);
        row.append(actions);
        return row;
    }

    function openDialog(title, ...content) {
        const dialog = $("dialog");
        $("dialog-title").textContent = title;
        $("dialog-content").replaceChildren(...content);
        if (!dialog.open) {
            dialogOpener = document.activeElement;
            dialog.showModal();
        }
        return ++state.dialog;
    }

    async function openStudent(id) {
        const request = openDialog("Карточка студента", element("p", "Загрузка контактов…"));
        try {
            const record = await api(`student/${id}`);
            if (!$("dialog").open || state.dialog !== request) return;
            const details = element("dl", undefined, "details");
            for (const [label, value] of [["ID", record.id], ["Телефон", record.phoneNumber], ["Электронная почта", record.email]]) {
                const group = element("div");
                group.append(element("dt", label), element("dd", value || "Не указано"));
                details.append(group);
            }
            const actions = element("div", undefined, "form-actions");
            if (mayWrite()) {
                actions.append(button("Удалить", "secondary", () => confirmDelete("students", record)),
                    button("Изменить", "primary", () => showEditor("students", record)));
            } else {
                actions.append(button("Закрыть", "secondary", () => $("dialog").close()));
            }
            openDialog(`${record.surname ?? ""} ${record.name ?? ""}`.trim() || "Карточка студента", details, actions);
        } catch (error) {
            if ($("dialog").open && state.dialog === request) openDialog("Карточка студента", element("p", error.message, "form-error"));
        }
    }

    function showEditor(section, record) {
        const users = section === "users";
        const template = $(users ? "user-form-template" : "student-form-template");
        const form = template.content.firstElementChild.cloneNode(true);
        form.elements.email.value = record?.email ?? "";
        if (users) {
            const password = form.elements.password;
            password.required = !record;
            if (record) {
                password.placeholder = "Оставьте пустым для сохранения пароля";
                form.querySelector(".form-actions").prepend(button("Удалить", "ghost", () => confirmDelete(section, record)));
            }
            for (const box of form.querySelectorAll("input[name=role]")) {
                box.checked = record ? record.roles.includes(box.value) : box.value === "TEACHER";
            }
        } else {
            for (const key of ["name", "surname", "phoneNumber"]) form.elements[key].value = record?.[key] ?? "";
            form.noValidate = Boolean(record);
        }
        form.querySelector("[data-cancel]").addEventListener("click", () => $("dialog").close());
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            void saveEditor(form, section, record);
        });
        const noun = users ? "пользователя" : "студента";
        openDialog(`${record ? "Изменить" : "Добавить"} ${noun}`, form);
        (users ? form.elements.email : form.elements.name).focus();
    }

    async function saveEditor(form, section, record) {
        const data = new FormData(form);
        const users = section === "users";
        const body = {};
        if (users) {
            body.email = data.get("email").trim();
            body.roles = data.getAll("role");
            body.password = data.get("password");
            if (!body.roles.length) {
                formError(form, "Выберите хотя бы одну роль.");
                return;
            }
        } else {
            for (const key of ["name", "surname", "phoneNumber", "email"]) {
                const value = data.get(key);
                if (record && value === (record[key] ?? "")) continue;
                if (!form.elements[key].reportValidity()) return;
                body[key === "phoneNumber" && !record ? "phone_number" : key] = value.trim();
            }
            if (record && !Object.keys(body).length) {
                $("dialog").close();
                return;
            }
        }
        const endpoint = users ? "users" : "student";
        await submitChange(form, async () => {
            await api(`${endpoint}/${record ? record.id : "save"}`, { method: record ? "PUT" : "POST", body });
        }, record ? "Изменения сохранены." : "Запись добавлена.");
    }

    function confirmDelete(section, record) {
        const users = section === "users";
        const form = element("form");
        const name = users ? record.email : `${record.surname ?? ""} ${record.name ?? ""}`.trim();
        const prompt = element("p", name ? `Удалить запись «${name}»?` : "Удалить запись?", "delete-text");
        const error = element("p", "", "form-error");
        error.hidden = true;
        error.setAttribute("role", "alert");
        const actions = element("div", undefined, "form-actions");
        const cancel = button("Отмена", "secondary", () => $("dialog").close());
        const remove = element("button", "Удалить", "button danger");
        remove.type = "submit";
        actions.append(cancel, remove);
        form.append(prompt, error, actions);
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            const endpoint = users ? "users" : "student";
            void submitChange(form, () => api(`${endpoint}/${record.id}`, { method: "DELETE" }), "Запись удалена.");
        });
        openDialog(users ? "Удаление пользователя" : "Удаление студента", form);
        cancel.focus();
    }

    async function submitChange(form, operation, message) {
        const submit = form.querySelector('[type="submit"]');
        if (submit.disabled) return;
        const session = state.session;
        const request = state.dialog;
        submit.disabled = true;
        formError(form);
        try {
            await operation();
            if (state.session !== session) return;
            if ($("dialog").open && state.dialog === request) $("dialog").close();
            state.userList = null;
            notify(message);
            void loadRows();
        } catch (error) {
            if ($("dialog").open && state.dialog === request) formError(form, error.message);
        } finally {
            submit.disabled = false;
        }
    }

    $("login-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const submit = form.querySelector('[type="submit"]');
        const data = new FormData(form);
        const email = data.get("email").trim();
        submit.disabled = true;
        formError(form);
        try {
            const login = await api("auth/login", { method: "POST", body: { email, password: data.get("password") }, signed: false });
            begin({ token: login.token, email, roles: roleNames(login.roles), expiresAt: Date.now() + login.expiresIn * 1000 });
            form.reset();
            $("content").focus();
        } catch (error) {
            formError(form, error.message);
        } finally {
            submit.disabled = false;
        }
    });
    $("logout").addEventListener("click", () => signOut());
    $("theme").addEventListener("click", () => {
        const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
        persist("localStorage", "dean-office.theme", next);
        setTheme(next);
    });
    for (const item of document.querySelectorAll(".nav-button")) {
        item.addEventListener("click", () => selectSection(item.dataset.section));
    }
    $("query").addEventListener("input", () => {
        clearTimeout(searchTimer);
        state.request++;
        searchTimer = setTimeout(() => {
            state.page = 0;
            void loadRows();
        }, 200);
    });
    $("list-tools").addEventListener("submit", (event) => {
        event.preventDefault();
        clearTimeout(searchTimer);
        state.page = 0;
        void loadRows();
    });
    for (const id of ["sort", "role-filter"]) $(id).addEventListener("change", () => {
        clearTimeout(searchTimer);
        state.page = 0;
        void loadRows();
    });
    $("previous").addEventListener("click", () => { state.page--; void loadRows(); });
    $("next").addEventListener("click", () => { state.page++; void loadRows(); });
    $("retry").addEventListener("click", () => { void loadRows(); });
    $("add-entry").addEventListener("click", () => showEditor(state.section));
    $("close-dialog").addEventListener("click", () => $("dialog").close());
    $("dialog").addEventListener("close", () => {
        if ($("dialog").open) return;
        state.dialog++;
        if (dialogOpener?.isConnected && !$("workspace").hidden) dialogOpener.focus();
    });
    $("preview-role").addEventListener("change", (event) => demoRole(event.target.value));

    const preferredTheme = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    setTheme(stored("localStorage", "dean-office.theme") || preferredTheme);
    if (preview) {
        $("preview-role-control").hidden = false;
        $("preview-note").hidden = false;
        $("logout").hidden = true;
        demoRole($("preview-role").value);
    } else {
        let session;
        try { session = JSON.parse(stored("sessionStorage", sessionKey)); } catch { session = null; }
        if (session?.token && Array.isArray(session.roles) && Number.isFinite(session.expiresAt)) {
            if (session.expiresAt > Date.now()) begin(session);
            else signOut("Сеанс завершён. Войдите снова.", false);
        }
    }
})();

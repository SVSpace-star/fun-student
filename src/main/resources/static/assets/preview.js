(() => {
    "use strict";

    if (location.protocol !== "file:" && new URLSearchParams(location.search).get("preview") !== "1") return;

    const demoNames = [
        ["Алина", "Алексеева"], ["Илья", "Белов"], ["Мария", "Волкова"], ["Максим", "Горбунов"],
        ["Артём", "Данилов"], ["София", "Егорова"], ["Даниил", "Жуков"], ["Варвара", "Зайцева"],
        ["Ксения", "Иванова"], ["Виктор", "Козлов"], ["Роман", "Лебедев"], ["Ирина", "Морозова"],
    ];
    const students = demoNames.map(([name, surname], index) => ({
        id: index + 1,
        name,
        surname,
        phoneNumber: index % 3 ? "" : "+7 900 000-00-00",
        email: `student${index + 1}@example.test`,
    }));
    const accounts = [
        { id: 1, email: "superadmin@example.test", roles: ["SUPER_ADMIN"] },
        { id: 2, email: "admin@example.test", roles: ["ADMIN"] },
        { id: 3, email: "teacher@example.test", roles: ["TEACHER"] },
        { id: 4, email: "coordinator@example.test", roles: ["ADMIN", "SUPER_ADMIN"] },
    ];
    const collator = new Intl.Collator("ru", { sensitivity: "base", numeric: true });
    let nextStudent = students.length + 1;
    let nextAccount = accounts.length + 1;

    function listStudents(params) {
        const query = (params.get("query") || "").trim().toLocaleLowerCase("ru");
        const sort = params.get("sort") || "id";
        const direction = params.get("direction") === "desc" ? -1 : 1;
        const filtered = students.filter(({ id, name, surname }) => {
            const names = [`${name} ${surname}`, `${surname} ${name}`];
            return !query || Number(query) === id || names.some((value) => value.toLocaleLowerCase("ru").includes(query));
        });
        filtered.sort((left, right) => {
            const order = sort === "id" ? left.id - right.id : collator.compare(left[sort], right[sort]);
            return order * direction || left.id - right.id;
        });
        const page = Number(params.get("page"));
        const size = Number(params.get("size")) || filtered.length;
        return {
            response: filtered.slice(page * size, (page + 1) * size).map(({ id, name, surname }) => ({ id, name, surname })),
            totalCount: filtered.length,
        };
    }

    function updateStudent(item, body) {
        const name = body.name ?? item.name;
        const surname = body.surname ?? item.surname;
        const phoneNumber = body.phone_number ?? body.phoneNumber ?? item.phoneNumber;
        const email = body.email ?? item.email;
        if (!name || !surname) throw new Error("Укажите имя и фамилию.");
        if (phoneNumber && !/^[+().\d -]+$/.test(phoneNumber)) throw new Error("Проверьте номер телефона.");
        Object.assign(item, { name, surname, phoneNumber, email });
    }

    function updateAccount(item, body) {
        if (!body.email || !body.roles?.length) throw new Error("Укажите электронную почту и выберите роль.");
        if (accounts.some((account) => account.id !== item.id && account.email.toLowerCase() === body.email.toLowerCase())) {
            throw new Error("Учётная запись с этой электронной почтой уже существует.");
        }
        item.email = body.email;
        item.roles = [...body.roles];
    }

    window.previewApi = async (path, method, body, roles) => {
        const [route, query] = path.split("?");
        const [resource, key] = route.split("/");
        const users = resource === "users";
        const admin = roles.includes("ADMIN");
        const allowed = users ? roles.includes("SUPER_ADMIN") : admin || (method === "GET" && roles.includes("TEACHER"));
        if (!allowed) throw new Error("У вашей учётной записи нет доступа к этой операции.");
        const records = users ? accounts : students;
        if (method === "GET" && !key) {
            return users ? { responses: structuredClone(accounts), totalCount: accounts.length } : listStudents(new URLSearchParams(query));
        }
        const update = users ? updateAccount : updateStudent;
        if (method === "POST" && key === "save") {
            const item = { id: users ? nextAccount : nextStudent };
            update(item, body);
            records.push(item);
            if (users) nextAccount++;
            else nextStudent++;
            return structuredClone(item);
        }
        const index = records.findIndex((item) => item.id === Number(key));
        if (index === -1) throw new Error("Запись не найдена.");
        if (method === "DELETE") {
            records.splice(index, 1);
            return null;
        }
        if (method === "PUT") update(records[index], body);
        return structuredClone(records[index]);
    };
})();

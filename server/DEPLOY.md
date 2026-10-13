# 服务器部署（Web 镜像）

以宝塔 nginx（/www/server/nginx）为例，任何 nginx 通用。

## 1. 放静态文件

```sh
mkdir -p /www/wwwroot/minis-sessions
# 把 web/ + 导出的 data/ sessions/ 都放进去
chown -R www:www /www/wwwroot/minis-sessions
```

⚠️ 坑：不要放 `/opt` 下 —— 很多发行版 /opt 是 700，php/nginx worker 读不到 → 神秘 403。

## 2. nginx location

加到需要保护的站点 vhost 里（我们挂在已有的 HTTPS 站点下）：

```nginx
location ^~ /sessions/ {
    alias /www/wwwroot/minis-sessions/;
    auth_basic "Private Sessions";
    auth_basic_user_file /www/server/nginx/conf/wxdesk_htpasswd;
    add_header Cache-Control "no-store" always;
    add_header Referrer-Policy "no-referrer" always;
}
```

```sh
nginx -t && nginx -s reload
```

## 3. htpasswd

```sh
printf "zy:$(openssl passwd -apr1 '你的密码')\n" >> /www/server/nginx/conf/wxdesk_htpasswd
```

## 4. 推送命令（配合沙箱插件）

```sh
cd <导出目录>/www
tar czf - -C . . | ssh user@server 'tar xzf - -C /www/wwwroot/minis-sessions && chown -R www:www /www/wwwroot/minis-sessions'
```

## 5. 验证

```sh
curl -o /dev/null -w "%{http_code}\n" https://your.site/sessions/          # 401
curl -u zy:密码 -o /dev/null -w "%{http_code}\n" https://your.site/sessions/  # 200
```

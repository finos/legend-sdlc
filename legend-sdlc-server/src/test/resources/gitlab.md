# Run gitlab container

```
sudo docker run -e external_url=https://localhost --detach   --hostname localhost   --publish 443:443 --publish 80:80 --publish 22:22   --name gitlab   --restart always   --volume $GITLAB_HOME/config:/etc/gitlab   --volume $GITLAB_HOME/logs:/var/log/gitlab   --volume $GITLAB_HOME/data:/var/opt/gitlab   --shm-size 256m   gitlab/gitlab-ee:latest
```

# Get root password

```
sudo docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

# Get Oauth password 

```

echo auth.txt2

grant_type=password&username=root&password=xxxxxxxxxxxx

curl -L -v --data "@auth.txt2" --request POST http://localhost/oauth/token

* Connection #0 to host localhost left intact
{"access_token":"xxxxxxxx","token_type":"Bearer","expires_in":7200,"refresh_token":"yyyyyyyyyyyyyyyyy","scope":"api","created_at":1679275389}
```


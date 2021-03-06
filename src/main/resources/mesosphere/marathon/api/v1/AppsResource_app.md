## GET `/v1/apps/{app_id}/tasks`

### Example

**Request:**

```
GET /v1/apps/myApp/tasks HTTP/1.1
Accept: application/json
Accept-Encoding: gzip, deflate, compress
Content-Type: application/json; charset=utf-8
Host: localhost:8080
User-Agent: HTTPie/0.7.2


```

**Response:**

```
HTTP/1.1 200 OK
Content-Type: application/json
Server: Jetty(8.y.z-SNAPSHOT)
Transfer-Encoding: chunked

{
    "myApp": [
        {
            "host": "mesos.vm", 
            "id": "myApp_2-1390501323992", 
            "ports": [
                31103, 
                31104
            ]
        }, 
        {
            "host": "mesos.vm", 
            "id": "myApp_0-1390501312927", 
            "ports": [
                31310, 
                31311
            ]
        }, 
        {
            "host": "mesos.vm", 
            "id": "myApp_1-1390501318981", 
            "ports": [
                31254, 
                31255
            ]
        }, 
        {
            "host": "mesos.vm", 
            "id": "myApp_3-1390501329997", 
            "ports": [
                31031, 
                31032
            ]
        }
    ]
}
```
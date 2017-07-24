IMAGE=$1
if [ -z $1 ]; then
    IMAGE=loklak/loklak_server:development
fi

echo "Setting image $IMAGE"

kubectl set image deployment/server --namespace=web server=$IMAGE

echo "Succesfully updated image"

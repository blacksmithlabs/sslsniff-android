#!/system/bin/sh
PATH=$PATH:/system/xbin

#Reset in case of previous usage
OPTIND=1

action=0
trap_all=0
trap_push=0
extra=""

TARGET_CHAIN="OUTPUT"

show_help()
{
    echo "usage: $0 [options] <action>"
    echo ""
    echo "This script sets up the iptables redirect rules for sslsniff"
    echo ""
    echo "ACTION:"
    echo "  start  Turn on the iptables rules"
    echo "  stop   Turn off the iptables rules"
    echo ""
    echo "OPTIONS:"
    echo "  -h     Show this help message"
    echo "  -a     Trap all traffic (default is to ignore local traffic)"
    echo "  -p     Trap push notification traffic (default off)"
    echo "  -i     Ignore hosts (see iptables --destination docs)"
    echo ""
}

while getopts "h?api:" OPTION; do
    case "$OPTION" in
    h|\?)
        show_help
        exit 0
        ;;
    a)  trap_all=1
        ;;
    p)  trap_push=1
        ;;
    i)  extra="$extra ! -d $OPTARG"
        ;;
    esac
done

shift $((OPTIND-1))
[ "$1" = "--" ] && shift

action=$1

case $action in
    start)
        iptables -t nat -F
        iptables -t nat -N SSLSNIFF_HTTP
        iptables -t nat -N SSLSNIFF_HTTPS
        
        # Always allow root (0) and adb (2000) out (which means this program)
        iptables -t nat -A SSLSNIFF_HTTP -m owner --uid-owner 0 -j RETURN
        iptables -t nat -A SSLSNIFF_HTTP -m owner --uid-owner 2000 -j RETURN
        iptables -t nat -A SSLSNIFF_HTTP -d 0.0.0.0/8 -j RETURN
        if [ $trap_all -eq 0 ]; then
            # If we haven't toggled to trap all, ignore local traffic
            iptables -t nat -A SSLSNIFF_HTTP -d 10.0.0.0/8 -j RETURN
            iptables -t nat -A SSLSNIFF_HTTP -d 127.0.0.0/8 -j RETURN
            iptables -t nat -A SSLSNIFF_HTTP -d 192.168.0.0/16 -j RETURN
        fi
        iptables -t nat -A SSLSNIFF_HTTP -p tcp -j REDIRECT --to-ports 8123
        
        # Always allow root (0) and adb (2000) out (which means this program)
        iptables -t nat -A SSLSNIFF_HTTPS -m owner --uid-owner 0 -j RETURN
        iptables -t nat -A SSLSNIFF_HTTPS -m owner --uid-owner 2000 -j RETURN
        iptables -t nat -A SSLSNIFF_HTTPS -d 0.0.0.0/8 -j RETURN
        if [ $trap_all == 0 ]; then
            # If we haven't toggled to trap all, ignore local traffic
            iptables -t nat -A SSLSNIFF_HTTPS -d 10.0.0.0/8 -j RETURN
            iptables -t nat -A SSLSNIFF_HTTPS -d 127.0.0.0/8 -j RETURN
            iptables -t nat -A SSLSNIFF_HTTPS -d 192.168.0.0/16 -j RETURN
        fi
        iptables -t nat -A SSLSNIFF_HTTPS -p tcp -j REDIRECT --to-ports 8124
        
        extra=""
        if [ "x$3" -ne "x" ]; then
            extra="! -d $3"
        fi
        
        #iptables -t nat -A $TARGET_CHAIN -p tcp --dport 80 $extra -j SSLSNIFF_HTTP
        #sslsniff doesn't support HTTP tracking probably can just use tcpdump here
        iptables -t nat -A $TARGET_CHAIN -p tcp --dport 443 $extra -j SSLSNIFF_HTTPS
        if [ $trap_push == 1 ]; then
            iptables -t nat -A $TARGET_CHAIN -p tcp --dport 5228 $extra -j SSLSNIFF_HTTPS
        fi
    ;;
    stop)
        iptables -t nat -F $TARGET_CHAIN
        iptables -t nat -F SSLSNIFF_HTTP
        iptables -t nat -X SSLSNIFF_HTTP
        iptables -t nat -F SSLSNIFF_HTTPS
        iptables -t nat -X SSLSNIFF_HTTPS
    ;;
    *)
        echo "Invalid action '$action'"
        echo ""
        show_help
        exit 1
    ;;
esac
